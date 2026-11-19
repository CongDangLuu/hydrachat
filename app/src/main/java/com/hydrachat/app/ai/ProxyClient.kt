package com.hydrachat.app.ai

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Calls the `generateNudge` Cloud Function (see backend/), which holds the Gemini key server-side.
 * Available only when the build was made with a google-services.json.
 */
class ProxyClient(private val context: Context) {
    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun generateNudge(request: NudgeRequest): Result<NudgeContent> {
        if (!isAvailable) return Result.failure(IllegalStateException("Firebase is not configured"))
        val payload = mapOf(
            "personaId" to request.personaId,
            "timeContext" to request.timeContext,
            "currentMl" to request.currentMl,
            "targetMl" to request.targetMl,
        )
        return try {
            ensureSignedIn()
            val result =FirebaseFunctions.getInstance(REGION).getHttpsCallable(FUNCTION_NAME).call(payload).await()
            Result.success(parse(result.getData()))
        } catch (e: FirebaseException) {
            // Covers network errors, sign-in failure, App Check rejection, the daily quota and server failures.
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    /** Silent anonymous sign-in: the proxy counts its daily quota per uid, and the user never sees a login. */
    private suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    private fun parse(data: Any?): NudgeContent {
        val map = requireNotNull(data as? Map<*, *>) { "Proxy response is not an object" }
        val nudge = NudgeContent(
            chatText = map["chatText"] as? String ?: "",
            quickReplyText = map["quickReplyText"] as? String ?: "",
            praiseText = map["praiseText"] as? String ?: "",
        )
        require(nudge.chatText.isNotBlank()) { "Proxy returned an empty chat text" }
        return nudge
    }

    private companion object {
        // Must match the region and name in backend/functions/src/index.ts.
        const val REGION = "asia-southeast1"
        const val FUNCTION_NAME = "generateNudge"
    }
}
