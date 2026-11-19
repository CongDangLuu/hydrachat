package com.hydrachat.app.data

import android.content.Context
import android.util.Log
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.domain.StoreCatalog
import com.hydrachat.app.domain.WorkSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UserSettings(
    val isOnboarded: Boolean = false,
    val personaId: String = Persona.GenZ.id,
    val weightKg: Int = 60,
    val isAge55Plus: Boolean = false,
    // Values equal to the default are not stored, so changing this would silently move existing users' targets.
    val targetMl: Int = 2000,
    val cupMl: Int = 250,
    val workStartMinute: Int = 8 * 60 + 30,
    val workEndMinute: Int = 17 * 60 + 30,
    val skipWeekends: Boolean = true,
    /** 0 means "auto": derived from target, cup size and work hours. */
    val intervalMinutes: Int = 0,
    val isSoundEnabled: Boolean = true,
    val apiKey: String = "",
    val hiddenApps: Set<String> = DEFAULT_HIDDEN_APPS,
    val points: Int = 0,
    val ownedItems: Set<String> = setOf(StoreCatalog.DEFAULT_SKIN_ID),
    val skinId: String = StoreCatalog.DEFAULT_SKIN_ID,
    val activePacks: Set<String> = emptySet(),
    val claimedQuests: Set<String> = emptySet(),
    val nextReminderAt: Long = 0,
    /** Gemini praise of the last reminder, used once when the user logs the drink it asked for. */
    val pendingPraise: String = "",
    val pendingPraiseAt: Long = 0,
) {
    val persona: Persona get() = Persona.fromId(personaId)
    val schedule: WorkSchedule get() = WorkSchedule(workStartMinute, workEndMinute, skipWeekends)
    val effectiveIntervalMinutes: Int
        get() = if (intervalMinutes > 0) intervalMinutes else HydrationMath.autoIntervalMinutes(targetMl, cupMl, schedule)

    companion object {
        val DEFAULT_HIDDEN_APPS = setOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "us.zoom.videomeetings",
            "com.microsoft.teams",
            "com.google.android.apps.tachyon",
        )
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hydra_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())

    val flow: StateFlow<UserSettings> get() = state
    val current: UserSettings get() = state.value

    @Synchronized
    fun update(transform: (UserSettings) -> UserSettings): UserSettings {
        val next = transform(state.value)
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(next)).apply()
        state.value = next
        return next
    }

    private fun load(): UserSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return UserSettings()
        return try {
            json.decodeFromString<UserSettings>(raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Stored settings are unreadable, falling back to defaults: ${e.javaClass.simpleName}")
            UserSettings()
        }
    }

    private companion object {
        const val TAG = "SettingsStore"
        const val KEY_SETTINGS = "settings"
    }
}
