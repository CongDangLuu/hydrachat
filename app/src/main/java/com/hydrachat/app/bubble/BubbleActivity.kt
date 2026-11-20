package com.hydrachat.app.bubble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.hydrachat.app.HydraApp
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.StoreCatalog
import com.hydrachat.app.reminder.ReminderNotifier
import com.hydrachat.app.ui.HydraTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Expanded view of the Android system bubble (API 30+), used when the overlay permission is missing.
 * It shows the same mini chat panel as the overlay service.
 */
class BubbleActivity : ComponentActivity() {
    private val state = BubbleUiState()
    private var isAnswering = false
    private val container get() = (application as HydraApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nudge = intent.toNudge()
        if (nudge == null) {
            finish()
            return
        }
        bind(nudge)
        setContent {
            HydraTheme {
                ChatPanel(
                    state = state,
                    onDrink = ::onDrink,
                    onSnooze = ::onSnooze,
                    onSendMessage = ::onSendMessage,
                    onClose = ::finish,
                )
            }
        }
    }

    private fun bind(nudge: NudgeContent) {
        val settings = container.settings.current
        state.nudge = nudge
        state.persona = settings.persona
        state.skin = StoreCatalog.skin(settings.skinId)
        state.targetMl = settings.targetMl.coerceAtLeast(1)
        state.cupMl = settings.cupMl
        state.hasUnread = false
        lifecycleScope.launch { state.totalMl = container.repository.todayTotalMl() }
    }

    private fun onDrink(amountMl: Int) {
        if (isAnswering) return
        isAnswering = true
        lifecycleScope.launch {
            // The user may collapse the bubble right away; the drink must still be saved.
            val result = withContext(NonCancellable) { container.repository.logDrink(amountMl) }
            state.totalMl = result.totalMl
            showAnswerThenClose("${result.praise}\n+${result.pointsEarned} giọt sương 💧", result.isPraiseFromAi)
        }
    }

    private fun onSnooze() {
        if (isAnswering) return
        isAnswering = true
        container.scheduler.scheduleInMinutes(HydrationMath.SNOOZE_MINUTES)
        lifecycleScope.launch {
            withContext(NonCancellable) { container.repository.recordSnooze() }
            showAnswerThenClose("Ok, ${HydrationMath.SNOOZE_MINUTES} phút nữa mình nhắc lại nha 🤫")
        }
    }

    private fun onSendMessage(text: String) {
        if (isAnswering || state.isSending) return
        state.isSending = true
        state.messages += PanelMessage(text, isFromUser = true)
        lifecycleScope.launch {
            // The user may collapse the bubble while waiting; the message and any drink must still be saved.
            val outcome = withContext(NonCancellable) { container.repository.sendUserMessage(text) }
            state.isSending = false
            state.messages += PanelMessage(outcome.replyText, isFromUser = false, isFromAi = outcome.isFromAi)
            val water = outcome.water ?: return@launch
            state.totalMl = water.totalMl
            isAnswering = true
            delay(CHAT_DRINK_COLLAPSE_MS)
            ReminderNotifier.cancelReminder(this@BubbleActivity)
            finish()
        }
    }

    private suspend fun showAnswerThenClose(text: String, isFromAi: Boolean = false) {
        state.answerText = text
        state.isAnswerFromAi = isFromAi
        state.phase = PanelPhase.Answered
        delay(AUTO_COLLAPSE_MS)
        // Cancelling the notification also removes its bubble.
        ReminderNotifier.cancelReminder(this)
        finish()
    }
}
