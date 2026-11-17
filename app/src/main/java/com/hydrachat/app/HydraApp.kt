package com.hydrachat.app

import android.app.Application
import com.hydrachat.app.ai.GeminiClient
import com.hydrachat.app.ai.OfflineLines
import com.hydrachat.app.ai.ProxyClient
import com.hydrachat.app.data.HydraDatabase
import com.hydrachat.app.data.HydraRepository
import com.hydrachat.app.data.SettingsStore
import com.hydrachat.app.reminder.ReminderNotifier
import com.hydrachat.app.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val database = HydraDatabase.create(app)
    val proxy = ProxyClient(app)
    val repository = HydraRepository(database, settings, OfflineLines(app), GeminiClient(), proxy)
    val scheduler = ReminderScheduler(app, settings, repository::isAiAvailable)
}

class HydraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // FirebaseInitProvider has already run; it only creates an app when google-services.json was bundled.
        if (container.proxy.isAvailable) AppCheckInstaller.install()
        ReminderNotifier.ensureChannels(this)
    }
}
