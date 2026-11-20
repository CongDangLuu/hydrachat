package com.hydrachat.app.data

import android.util.Log
import com.hydrachat.app.ai.ChatReply
import com.hydrachat.app.ai.ChatRequest
import com.hydrachat.app.ai.ChatTurn
import com.hydrachat.app.ai.GeminiClient
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.ai.NudgeRequest
import com.hydrachat.app.ai.OfflineLines
import com.hydrachat.app.ai.ProxyClient
import com.hydrachat.app.domain.DailyQuest
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.ItemKind
import com.hydrachat.app.domain.StoreItem
import java.time.LocalDate
import java.time.ZoneId

data class DrinkResult(
    val praise: String,
    val isPraiseFromAi: Boolean,
    val totalMl: Int,
    val targetMl: Int,
    val pointsEarned: Int,
    val isGoalJustReached: Boolean,
)

/** Water added by one drink: the new total and the points it earned. */
data class WaterProgress(
    val amountMl: Int,
    val totalMl: Int,
    val targetMl: Int,
    val pointsEarned: Int,
    val isGoalJustReached: Boolean,
)

/** The persona's answer to a typed message, and the drink it logged if the message reported one. */
data class ChatOutcome(val replyText: String, val isFromAi: Boolean, val water: WaterProgress?)

class HydraRepository(
    private val db: HydraDatabase,
    private val settings: SettingsStore,
    private val offline: OfflineLines,
    private val gemini: GeminiClient,
    private val proxy: ProxyClient,
) {
    suspend fun todayTotalMl(): Int = db.waterDao().sumSince(todayStart())

    /** Prefetched Gemini line if a fresh one exists, otherwise an offline line. Never hits the network. */
    suspend fun nextNudge(): NudgeContent {
        val current = settings.current
        val minCreatedAt = System.currentTimeMillis() - NUDGE_FRESHNESS_MS
        val cached = db.nudgeDao().latestFor(current.personaId, minCreatedAt)
            ?: return offline.pick(current.persona, current.activePacks)
        db.nudgeDao().consume(cached.id, minCreatedAt)
        // Only Gemini lines are ever cached; offline lines are picked on the spot.
        return NudgeContent(cached.chatText, cached.quickReplyText, cached.praiseText, isFromAi = true)
    }

    /** A personal key (direct Gemini call) or the Firebase proxy; without either, only offline lines are used. */
    fun isAiAvailable(): Boolean = settings.current.apiKey.isNotBlank() || proxy.isAvailable

    /** Fetches a Gemini line for the reminder at `forTimeMillis` and caches it for the next bubble. */
    suspend fun prefetchNudge(forTimeMillis: Long): Result<NudgeContent> {
        val current = settings.current
        if (!isAiAvailable()) return Result.failure(IllegalStateException("Chưa có API key"))
        val request = aiContext(forTimeMillis)
        val result = if (current.apiKey.isNotBlank()) {
            gemini.generateNudge(current.apiKey, request)
        } else {
            proxy.generateNudge(request)
        }
        val nudge = result.getOrElse { error ->
            Log.w(TAG, "Gemini prefetch failed, the offline library will be used: ${error.javaClass.simpleName}")
            return Result.failure(error)
        }
        db.nudgeDao().insert(
            CachedNudge(
                personaId = current.personaId,
                chatText = nudge.chatText,
                quickReplyText = nudge.quickReplyText,
                praiseText = nudge.praiseText,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return Result.success(nudge.copy(isFromAi = true))
    }

    suspend fun recordNudgeShown(nudge: NudgeContent) {
        val now = System.currentTimeMillis()
        db.chatDao().deleteOlderThan(now - CHAT_RETENTION_MS)
        db.chatDao().insert(
            ChatMessage(
                timestamp = now,
                isFromUser = false,
                text = nudge.chatText,
                isFromAi = nudge.isFromAi,
            ),
        )
        val praise = if (nudge.isFromAi) nudge.praiseText else ""
        settings.update { it.copy(pendingPraise = praise, pendingPraiseAt = now) }
    }

    suspend fun recordBotMessage(text: String) {
        db.chatDao().insert(ChatMessage(timestamp = System.currentTimeMillis(), isFromUser = false, text = text))
    }

    suspend fun logDrink(amountMl: Int): DrinkResult {
        val now = System.currentTimeMillis()
        val water = recordWater(amountMl, now)
        val aiPraise = takePendingPraise(now)
        val praise = when {
            water.isGoalJustReached -> goalMessage(water.targetMl)
            aiPraise != null -> aiPraise
            else -> offline.praise(settings.current.persona)
        }
        val isPraiseFromAi = !water.isGoalJustReached && aiPraise != null
        db.chatDao().insert(ChatMessage(timestamp = now, isFromUser = true, text = "🥤 Đã uống $amountMl ml"))
        db.chatDao().insert(
            ChatMessage(timestamp = now + 1, isFromUser = false, text = praise, isFromAi = isPraiseFromAi),
        )
        return DrinkResult(
            praise,
            isPraiseFromAi,
            water.totalMl,
            water.targetMl,
            water.pointsEarned,
            water.isGoalJustReached,
        )
    }

    /** Saves a typed message, asks Gemini for the persona's answer and logs a drink the message reports. */
    suspend fun sendUserMessage(text: String): ChatOutcome {
        val history = db.chatDao().latest(CHAT_HISTORY_TURNS).reversed()
            .map { ChatTurn(it.isFromUser, it.text.take(HydrationMath.MAX_CHAT_MESSAGE_LENGTH)) }
        db.chatDao().insert(ChatMessage(timestamp = System.currentTimeMillis(), isFromUser = true, text = text))
        val reply = requestChatReply(text, history)
        val now = System.currentTimeMillis()
        val water = reply?.drankMl?.takeIf { it > 0 }?.let { recordWater(it, now) }
        // A drink reported in chat answers the last reminder, so its prepared praise must not be reused.
        if (water != null) settings.update { it.copy(pendingPraise = "") }
        val replyText = listOfNotNull(
            reply?.replyText ?: if (isAiAvailable()) CHAT_OFFLINE_REPLY else CHAT_NO_AI_REPLY,
            water?.let { "🥤 Đã ghi ${it.amountMl} ml · +${it.pointsEarned} giọt sương 💧" },
            water?.takeIf { it.isGoalJustReached }?.let { goalMessage(it.targetMl) },
        ).joinToString("\n")
        db.chatDao().insert(
            ChatMessage(timestamp = now, isFromUser = false, text = replyText, isFromAi = reply != null),
        )
        return ChatOutcome(replyText, reply != null, water)
    }

    private suspend fun requestChatReply(text: String, history: List<ChatTurn>): ChatReply? {
        if (!isAiAvailable()) return null
        val current = settings.current
        val request = ChatRequest(aiContext(System.currentTimeMillis()), current.cupMl, history, text)
        val result = if (current.apiKey.isNotBlank()) {
            gemini.generateChatReply(current.apiKey, request)
        } else {
            proxy.chatReply(request)
        }
        return result.getOrElse { error ->
            Log.w(TAG, "Gemini chat reply failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private suspend fun aiContext(atMillis: Long): NudgeRequest {
        val current = settings.current
        return NudgeRequest(
            personaId = current.persona.id,
            personaRole = current.persona.promptRole,
            timeContext = HydrationMath.timeContext(atMillis, ZoneId.systemDefault()),
            currentMl = todayTotalMl(),
            targetMl = current.targetMl,
        )
    }

    private suspend fun recordWater(amountMl: Int, now: Long): WaterProgress {
        val before = todayTotalMl()
        db.waterDao().insert(WaterLog(timestamp = now, amountMl = amountMl))
        val total = before + amountMl
        val target = settings.current.targetMl
        val isGoalJustReached = before < target && total >= target
        val earned = amountMl * HydrationMath.POINTS_PER_100_ML / ML_PER_POINT_UNIT +
            if (isGoalJustReached) HydrationMath.GOAL_BONUS_POINTS else 0
        settings.update { it.copy(points = it.points + earned) }
        return WaterProgress(amountMl, total, target, earned, isGoalJustReached)
    }

    private fun goalMessage(targetMl: Int): String =
        "🎉 Đạt mục tiêu $targetMl ml hôm nay rồi! Thưởng nóng ${HydrationMath.GOAL_BONUS_POINTS} giọt sương."

    /** Returns the last reminder's Gemini praise once; a stale one would answer a reminder from hours ago. */
    private fun takePendingPraise(now: Long): String? {
        val current = settings.current
        if (current.pendingPraise.isBlank()) return null
        settings.update { it.copy(pendingPraise = "") }
        return current.pendingPraise.takeIf { now - current.pendingPraiseAt < PENDING_PRAISE_TTL_MS }
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
        // Longer than the widest reminder interval (90 min), so a drink before the next reminder still gets it.
        const val PENDING_PRAISE_TTL_MS = 2 * 60 * 60 * 1000L
        // Must not exceed MAX_HISTORY_TURNS in backend/functions/src/chat.ts.
        const val CHAT_HISTORY_TURNS = 8
        const val CHAT_OFFLINE_REPLY = "Mình đang không kết nối được Gemini nên chưa trả lời được 😅 Nhớ uống nước nha 💧"
        const val CHAT_NO_AI_REPLY = "Bật Gemini trong phần Cài đặt để mình trò chuyện với bạn được nha 💬"
    }
}
