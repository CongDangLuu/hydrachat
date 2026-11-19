package com.hydrachat.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrachat.app.HydraApp
import com.hydrachat.app.bubble.BubbleService
import com.hydrachat.app.reminder.ReminderNotifier

private data class Tab(val emoji: String, val label: String)

private val tabs = listOf(Tab("💬", "Phòng chat"), Tab("🎁", "Nhiệm vụ"), Tab("📊", "Thống kê"))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HydraTheme { HydraRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as HydraApp).container.screenVisibility.onStart()
        // The chat room already shows every reminder, so a bubble or notification on top of the app is redundant.
        BubbleService.dismiss(this)
        ReminderNotifier.cancelReminder(this)
    }

    override fun onStop() {
        (application as HydraApp).container.screenVisibility.onStop()
        super.onStop()
    }
}

@Composable
private fun HydraRoot(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDay() }
    if (!settings.isOnboarded) {
        OnboardingScreen(viewModel)
        return
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.emoji, fontSize = 20.sp) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> StoreScreen(viewModel)
                else -> StatsScreen(viewModel)
            }
        }
    }
}
