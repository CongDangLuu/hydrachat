package com.hydrachat.app.data

import android.util.Log
import com.hydrachat.app.ai.GeminiClient
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.ai.NudgeRequest
import com.hydrachat.app.ai.OfflineLines
import com.hydrachat.app.domain.DailyQuest
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.ItemKind
import com.hydrachat.app.domain.StoreItem
import java.time.LocalDate
import java.time.ZoneId

data class DrinkResult(
    val praise: String,
    val totalMl: Int,
    val targetMl: Int,
    val pointsEarned: Int,
    val isGoalJustReached: Boolean,
)

class HydraRepository(
    private val db: HydraDatabase,
    private val settings: SettingsStore,
    private val offline: OfflineLines,
    private val gemini: GeminiClient,
) {
    suspend fun todayTotalMl(): Int = db.waterDao().sumSince(todayStart())

    /** Prefetched Gemini line if a fresh one exists, otherwise an offline line. Never hits the network. */
    suspend fun nextNudge(): NudgeContent {
        val current = settings.current
        val minCreatedAt = System.currentTimeMillis() - NUDGE_FRESHNESS_MS
        val cached = db.nudgeDao().latestFor(current.personaId, minCreatedAt)
            ?: return offline.pick(current.persona, current.activePacks)
        db.nudgeDao().consume(cached.id, minCreatedAt)
        return NudgeContent(cached.chatText, cached.quickReplyText, cached.miniQuest)
    }

    /** Returns true when a Gemini line was fetched and cached for the reminder at `forTimeMillis`. */
    suspend fun prefetchNudge(forTimeMillis: Long): Boolean {
        val current = settings.current
        if (current.apiKey.isBlank()) return false
        val request = NudgeRequest(
            personaRole = current.persona.promptRole,
            timeContext = HydrationMath.timeContext(forTimeMillis, ZoneId.systemDefault()),
            currentMl = todayTotalMl(),
            targetMl = current.targetMl,
        )
        val nudge = gemini.generateNudge(current.apiKey, request).getOrElse { error ->
            Log.w(TAG, "Gemini prefetch failed, the offline library will be used: ${error.javaClass.simpleName}")
            return false
        }
        db.nudgeDao().insert(
            CachedNudge(
                personaId = current.personaId,
                chatText = nudge.chatText,
                quickReplyText = nudge.quickReplyText,
                miniQuest = nudge.miniQuest,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun recordNudgeShown(nudge: NudgeContent) {
        val now = System.currentTimeMillis()
        db.chatDao().deleteOlderThan(now - CHAT_RETENTION_MS)
        db.chatDao().insert(ChatMessage(timestamp = now, isFromUser = false, text = nudge.chatText, quest = nudge.miniQuest))
    }

    suspend fun recordBotMessage(text: String) {
        db.chatDao().insert(ChatMessage(timestamp = System.currentTimeMillis(), isFromUser = false, text = text))
    }

    suspend fun logDrink(amountMl: Int): DrinkResult {
        val now = System.currentTimeMillis()
        val before = todayTotalMl()
        db.waterDao().insert(WaterLog(timestamp = now, amountMl = amountMl))
        val total = before + amountMl
        val target = settings.current.targetMl
        val isGoalJustReached = before < target && total >= target
        val earned = amountMl * HydrationMath.POINTS_PER_100_ML / ML_PER_POINT_UNIT +
            if (isGoalJustReached) HydrationMath.GOAL_BONUS_POINTS else 0
        settings.update { it.copy(points = it.points + earned) }

        val praise = if (isGoalJustReached) {
            "🎉 Đạt mục tiêu $target ml hôm nay rồi! Thưởng nóng ${HydrationMath.GOAL_BONUS_POINTS} giọt sương."
        } else {
            offline.praise(settings.current.persona)
        }
        db.chatDao().insert(ChatMessage(timestamp = now, isFromUser = true, text = "🥤 Đã uống $amountMl ml"))
        db.chatDao().insert(ChatMessage(timestamp = now + 1, isFromUser = false, text = praise))
        return DrinkResult(praise, total, target, earned, isGoalJustReached)
    }

    suspend fun recordSnooze() {
        db.chatDao().insert(
            ChatMessage(
                timestamp = System.currentTimeMillis(),
                isFromUser = true,
                text = "🤫 Đang họp, nhắc lại sau ${HydrationMath.SNOOZE_MINUTES}p",
            ),
        )
    }

    suspend fun completeMiniQuest(questText: String) {
        db.questDao().insert(QuestLog(timestamp = System.currentTimeMillis(), questText = questText))
        settings.update { it.copy(points = it.points + HydrationMath.MINI_QUEST_POINTS) }
    }

    /** Buys the item if affordable; returns false when it is already owned or too expensive. */
    fun buy(item: StoreItem): Boolean {
        var isBought = false
        settings.update { current ->
            if (item.id in current.ownedItems || current.points < item.price) {
                current
            } else {
                isBought = true
                val packs = if (item.kind == ItemKind.Pack) current.activePacks + item.id else current.activePacks
                current.copy(points = current.points - item.price, ownedItems = current.ownedItems + item.id, activePacks = packs)
            }
        }
        return isBought
    }

    fun equipSkin(skinId: String) {
        settings.update { if (skinId in it.ownedItems) it.copy(skinId = skinId) else it }
    }

    fun togglePack(packId: String) {
        settings.update { current ->
            if (packId !in current.ownedItems) return@update current
            val packs = if (packId in current.activePacks) current.activePacks - packId else current.activePacks + packId
            current.copy(activePacks = packs)
        }
    }

    fun claimQuest(quest: DailyQuest) {
        val dayPrefix = "${LocalDate.now()}:"
        val key = dayPrefix + quest.id
        settings.update { current ->
            if (key in current.claimedQuests) return@update current
            // Claims from earlier days are no longer needed.
            val todayClaims = current.claimedQuests.filter { it.startsWith(dayPrefix) }.toSet()
            current.copy(points = current.points + quest.reward, claimedQuests = todayClaims + key)
        }
    }

    fun isQuestClaimed(quest: DailyQuest, claimed: Set<String>): Boolean = "${LocalDate.now()}:${quest.id}" in claimed

    private fun todayStart(): Long = HydrationMath.dayStartMillis(System.currentTimeMillis(), ZoneId.systemDefault())

    private companion object {
        const val TAG = "HydraRepository"
        const val NUDGE_FRESHNESS_MS = 30 * 60 * 1000L
        const val CHAT_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
        const val ML_PER_POINT_UNIT = 100
    }
}
