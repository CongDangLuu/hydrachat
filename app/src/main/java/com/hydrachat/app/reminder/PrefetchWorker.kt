package com.hydrachat.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hydrachat.app.HydraApp

/** Fetches the next Gemini line a few minutes early so the bubble appears with no network wait. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HydraApp).container
        val triggerAt = inputData.getLong(KEY_TRIGGER_AT, System.currentTimeMillis())
        container.repository.prefetchNudge(triggerAt)
        // A failed fetch is fine: the reminder falls back to the offline library.
        return Result.success()
    }

    companion object {
        const val KEY_TRIGGER_AT = "trigger_at"
    }
}
