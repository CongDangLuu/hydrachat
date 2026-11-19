package com.hydrachat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydrachat.app.HydraApp
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.bubble.BubbleService
import com.hydrachat.app.data.ChatMessage
import com.hydrachat.app.data.UserSettings
import com.hydrachat.app.data.WaterLog
import com.hydrachat.app.domain.DailyQuest
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.domain.StoreItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class OnboardingResult(
    val persona: Persona,
    val weightKg: Int,
    val isAge55Plus: Boolean,
    val workStartMinute: Int,
    val workEndMinute: Int,
    val cupMl: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as HydraApp).container
    private val repository = container.repository
    private val dayStart = MutableStateFlow(currentDayStart())

    val settings: StateFlow<UserSettings> = container.settings.flow

    val todayLogs: StateFlow<List<WaterLog>> = dayStart
        .flatMapLatest { container.database.waterDao().observeSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val todayChat: StateFlow<List<ChatMessage>> = dayStart
        .flatMapLatest { container.database.chatDao().observeSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val historyLogs: StateFlow<List<WaterLog>> = dayStart
        .flatMapLatest { container.database.waterDao().observeSince(it - TimeUnit.DAYS.toMillis(HISTORY_DAYS)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Called on resume so "today" rolls over when the app stays open past midnight. */
    fun refreshDay() {
        dayStart.value = currentDayStart()
    }

    fun canScheduleExact(): Boolean = container.scheduler.canScheduleExact()

    fun isProxyAvailable(): Boolean = container.proxy.isAvailable

    fun finishOnboarding(result: OnboardingResult) {
        container.settings.update {
            it.copy(
                isOnboarded = true,
                personaId = result.persona.id,
                weightKg = result.weightKg,
                isAge55Plus = result.isAge55Plus,
                targetMl = HydrationMath.dailyTargetMl(result.weightKg, result.isAge55Plus),
                cupMl = result.cupMl,
                workStartMinute = result.workStartMinute,
                workEndMinute = result.workEndMinute,
            )
        }
        container.scheduler.scheduleNext()
        viewModelScope.launch {
            repository.recordBotMessage("${result.persona.hello} Tới giờ là mình nhắc, bạn cứ yên tâm làm việc nhé!")
        }
    }

    /** Changes a setting that affects reminder timing and re-arms the alarm. */
    fun updateSchedule(transform: (UserSettings) -> UserSettings) {
        container.settings.update(transform)
        container.scheduler.scheduleNext()
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        container.settings.update(transform)
    }

    fun logDrink(amountMl: Int) {
        viewModelScope.launch { repository.logDrink(amountMl) }
    }

    /** Onboarding demo of the reminder bubble; regular reminders skip it while the app is open. */
    fun showWelcomeBubble(nudge: NudgeContent) {
        BubbleService.show(getApplication(), nudge)
    }

    /** The in-app test button: posts a reminder line straight into the chat room. */
    fun remindInChat() {
        viewModelScope.launch { repository.recordNudgeShown(freshNudge()) }
    }

    /** A manual test has no prefetch 5 minutes ahead, so ask Gemini now and fall back to offline lines. */
    private suspend fun freshNudge(): NudgeContent {
        if (repository.isAiAvailable()) repository.prefetchNudge(System.currentTimeMillis())
        return repository.nextNudge()
    }

    /** Calls Gemini right now so the user can check the key; the line is used by the next bubble. */
    fun testGemini(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.prefetchNudge(System.currentTimeMillis())
            onResult(
                result.fold(
                    onSuccess = { "✅ ${it.chatText}\n👏 ${it.praiseText}\n🔘 ${it.quickReplyText}" },
                    onFailure = { "❌ ${it.message ?: it.javaClass.simpleName}" },
                ),
            )
        }
    }

    fun buy(item: StoreItem): Boolean = repository.buy(item)

    fun equipSkin(skinId: String) = repository.equipSkin(skinId)

    fun togglePack(packId: String) = repository.togglePack(packId)

    fun claimQuest(quest: DailyQuest) = repository.claimQuest(quest)

    fun isQuestClaimed(quest: DailyQuest, claimed: Set<String>): Boolean = repository.isQuestClaimed(quest, claimed)

    private fun currentDayStart(): Long =
        HydrationMath.dayStartMillis(System.currentTimeMillis(), ZoneId.systemDefault())

    companion object {
        const val HISTORY_DAYS = 35L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
