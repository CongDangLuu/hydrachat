package com.hydrachat.app.bubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hydrachat.app.HydraApp
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.StoreCatalog
import com.hydrachat.app.reminder.ReminderNotifier
import com.hydrachat.app.ui.HydraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Short-lived foreground service that draws the chat head, its preview, the dismiss target and the
 * mini chat panel as overlay windows. It stops itself as soon as the user answers or dismisses.
 */
class BubbleService : Service() {
    private val state = BubbleUiState()
    private val owner = OverlayLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var bubbleView: View? = null
    private var previewView: View? = null
    private var panelView: View? = null
    private var dismissView: View? = null
    private var snapAnimator: ValueAnimator? = null
    private var previewJob: Job? = null
    private var timeoutJob: Job? = null
    private var isAnswering = false

    private val container get() = (application as HydraApp).container
    private val density get() = resources.displayMetrics.density
    private val screenWidth get() = resources.displayMetrics.widthPixels
    private val screenHeight get() = resources.displayMetrics.heightPixels
    private val bubbleSizePx get() = (BUBBLE_BOX_DP * density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nudge = intent?.toNudge()
        val isForeground = enterForeground()
        if (nudge == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isForeground || !Settings.canDrawOverlays(this)) {
            showFallback(this, nudge)
            stopSelf()
            return START_NOT_STICKY
        }
        showReminder(nudge)
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(
                this,
                ReminderNotifier.SERVICE_NOTIFICATION_ID,
                ReminderNotifier.serviceNotification(this),
                type,
            )
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException.
            Log.w(TAG, "startForeground was refused: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (bubbleView != null) snapToEdge()
    }

    override fun onDestroy() {
        removeAllViews()
        scope.cancel()
        owner.destroy()
        super.onDestroy()
    }

    private fun showReminder(nudge: NudgeContent) {
        val settings = container.settings.current
        isAnswering = false
        state.nudge = nudge
        state.persona = settings.persona
        state.skin = StoreCatalog.skin(settings.skinId)
        state.targetMl = settings.targetMl.coerceAtLeast(1)
        state.cupMl = settings.cupMl
        state.phase = PanelPhase.Asking
        state.hasUnread = true
        scope.launch { state.totalMl = container.repository.todayTotalMl() }

        removeView(panelView)
        panelView = null
        if (bubbleView == null) addBubble()
        showPreview()
        if (settings.isSoundEnabled) playSound()
        restartTimeout()
    }

    // --- windows -------------------------------------------------------------------------------

    private fun overlayParams(width: Int, height: Int, extraFlags: Int = 0) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or extraFlags,
        PixelFormat.TRANSLUCENT,
    )

    private fun composeWindow(content: @Composable () -> Unit): InterceptingFrame {
        val root = InterceptingFrame(this)
        owner.attachTo(root)
        root.addView(ComposeView(this).apply { setContent { HydraTheme(content) } })
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val wrap = WindowManager.LayoutParams.WRAP_CONTENT
        bubbleParams = overlayParams(wrap, wrap, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - bubbleSizePx
            y = (screenHeight * INITIAL_Y_RATIO).toInt()
        }
        val view = composeWindow { BubbleHead(state) }
        view.interceptTouches = true
        view.setOnTouchListener(BubbleTouchListener())
        windowManager.addView(view, bubbleParams)
        bubbleView = view
    }

    private fun showPreview() {
        hidePreview()
        val wrap = WindowManager.LayoutParams.WRAP_CONTENT
        val isOnLeft = bubbleParams.x + bubbleSizePx / 2 < screenWidth / 2
        val params = overlayParams(wrap, wrap, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            gravity = Gravity.TOP or (if (isOnLeft) Gravity.START else Gravity.END)
            x = bubbleSizePx
            y = bubbleParams.y
        }
        val view = composeWindow { BubblePreview(state) }
        windowManager.addView(view, params)
        previewView = view
        previewJob = scope.launch {
            delay(PREVIEW_DURATION_MS)
            hidePreview()
        }
    }

    private fun hidePreview() {
        previewJob?.cancel()
        removeView(previewView)
        previewView = null
    }

    private fun togglePanel() {
        if (panelView != null) {
            hidePanel()
            return
        }
        state.hasUnread = false
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (screenHeight * PANEL_HEIGHT_RATIO).toInt(),
        ).apply { gravity = Gravity.BOTTOM }
        val view = composeWindow {
            ChatPanel(
                state = state,
                onDrink = ::onDrink,
                onSnooze = ::onSnooze,
                onClose = ::hidePanel,
            )
        }
        windowManager.addView(view, params)
        panelView = view
        restartTimeout()
    }

    private fun hidePanel() {
        removeView(panelView)
        panelView = null
    }

    private fun showDismissTarget() {
        if (dismissView != null) return
        val wrap = WindowManager.LayoutParams.WRAP_CONTENT
        val params = overlayParams(wrap, wrap, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (DISMISS_BOTTOM_MARGIN_DP * density).toInt()
        }
        val view = composeWindow { DismissTarget(state) }
        windowManager.addView(view, params)
        dismissView = view
    }

    private fun hideDismissTarget() {
        state.isOverDismiss = false
        removeView(dismissView)
        dismissView = null
    }

    private fun removeView(view: View?) {
        if (view != null && view.parent != null) windowManager.removeView(view)
    }

    private fun removeAllViews() {
        snapAnimator?.cancel()
        listOf(bubbleView, previewView, panelView, dismissView).forEach(::removeView)
        bubbleView = null
        previewView = null
        panelView = null
        dismissView = null
    }

    private fun dismissAll() {
        removeAllViews()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- answers -------------------------------------------------------------------------------

    private fun onDrink(amountMl: Int) {
        if (isAnswering) return
        isAnswering = true
        scope.launch {
            val result = container.repository.logDrink(amountMl)
            state.totalMl = result.totalMl
            showAnswerThenClose("${result.praise}\n+${result.pointsEarned} giọt sương 💧")
        }
    }

    private fun onSnooze() {
        if (isAnswering) return
        isAnswering = true
        container.scheduler.scheduleInMinutes(HydrationMath.SNOOZE_MINUTES)
        scope.launch {
            container.repository.recordSnooze()
            showAnswerThenClose("Ok, ${HydrationMath.SNOOZE_MINUTES} phút nữa mình nhắc lại nha 🤫")
        }
    }

    private suspend fun showAnswerThenClose(text: String) {
        timeoutJob?.cancel()
        state.answerText = text
        state.phase = PanelPhase.Answered
        delay(AUTO_COLLAPSE_MS)
        dismissAll()
    }

    // --- bubble physics ------------------------------------------------------------------------

    private fun moveBubble(x: Int, y: Int) {
        bubbleParams.x = x
        bubbleParams.y = y
        bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
    }

    private fun isBubbleOverDismissTarget(): Boolean {
        val bubbleCenterX = bubbleParams.x + bubbleSizePx / 2f
        val bubbleCenterY = bubbleParams.y + bubbleSizePx / 2f
        val targetCenterX = screenWidth / 2f
        val targetCenterY = screenHeight - (DISMISS_BOTTOM_MARGIN_DP + DISMISS_TARGET_DP / 2f) * density
        val distance = hypot(bubbleCenterX - targetCenterX, bubbleCenterY - targetCenterY)
        return distance < DISMISS_RADIUS_DP * density
    }

    private fun snapToEdge() {
        val isOnLeft = bubbleParams.x + bubbleSizePx / 2 < screenWidth / 2
        val targetX = if (isOnLeft) 0 else screenWidth - bubbleSizePx
        val maxY = (screenHeight - bubbleSizePx * 2).coerceAtLeast(0)
        val targetY = bubbleParams.y.coerceIn(0, maxY)
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            duration = SNAP_DURATION_MS
            interpolator = OvershootInterpolator()
            addUpdateListener { moveBubble(it.animatedValue as Int, targetY) }
            start()
        }
    }

    private fun playSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(this, uri)?.play()
    }

    private fun restartTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            dismissAll()
        }
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onDown(event)
                MotionEvent.ACTION_MOVE -> onMove(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onUp()
            }
            return true
        }

        private fun onDown(event: MotionEvent) {
            snapAnimator?.cancel()
            hidePreview()
            downRawX = event.rawX
            downRawY = event.rawY
            startX = bubbleParams.x
            startY = bubbleParams.y
            isDragging = false
        }

        private fun onMove(event: MotionEvent) {
            val deltaX = event.rawX - downRawX
            val deltaY = event.rawY - downRawY
            if (!isDragging && hypot(deltaX, deltaY) > touchSlop) {
                isDragging = true
                hidePanel()
                showDismissTarget()
            }
            if (isDragging) {
                moveBubble(startX + deltaX.toInt(), startY + deltaY.toInt())
                state.isOverDismiss = isBubbleOverDismissTarget()
            }
        }

        private fun onUp() {
            if (!isDragging) {
                togglePanel()
                return
            }
            val shouldDismiss = isBubbleOverDismissTarget()
            hideDismissTarget()
            if (shouldDismiss) dismissAll() else snapToEdge()
        }
    }

    /** Lets the bubble root take every touch so drags are tracked in raw screen coordinates. */
    private class InterceptingFrame(context: Context) : FrameLayout(context) {
        var interceptTouches = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean = interceptTouches
    }

    companion object {
        private const val INITIAL_Y_RATIO = 0.3f
        private const val PANEL_HEIGHT_RATIO = 0.42f
        private const val DISMISS_BOTTOM_MARGIN_DP = 48
        private const val DISMISS_RADIUS_DP = 90
        private const val SNAP_DURATION_MS = 250L
        private const val PREVIEW_DURATION_MS = 5_000L
        private const val IDLE_TIMEOUT_MS = 3 * 60 * 1000L
        private const val TAG = "BubbleService"

        /** Shows the overlay bubble, or the fallback notification when the overlay cannot be used. */
        fun show(context: Context, nudge: NudgeContent) {
            if (Settings.canDrawOverlays(context) && start(context, nudge)) return
            showFallback(context, nudge)
        }

        /** Returns false when Android refuses a foreground service start, e.g. from the background on 12+. */
        private fun start(context: Context, nudge: NudgeContent): Boolean {
            val intent = Intent(context, BubbleService::class.java).putNudge(nudge)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Bubble service start was refused: ${e.javaClass.simpleName}")
                false
            }
        }

        private fun showFallback(context: Context, nudge: NudgeContent) {
            val settings = (context.applicationContext as HydraApp).container.settings.current
            ReminderNotifier.showReminder(context, settings, nudge)
        }
    }
}
