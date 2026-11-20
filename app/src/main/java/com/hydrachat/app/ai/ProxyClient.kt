package com.hydrachat.app.ai

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Calls the `generateNudge` and `chatReply` Cloud Functions (see backend/), which hold the Gemini key server-side.
 * Available only when the build was made with a google-services.json.
 */
class ProxyClient(private val context: Context) {
    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun generateNudge(request: NudgeRequest): Result<NudgeContent> =
        callFunction(NUDGE_FUNCTION, contextPayload(request), ::parseNudge)

    suspend fun chatReply(request: ChatRequest): Result<ChatReply> {
        val payload = contextPayload(request.context) + mapOf(
            "cupMl" to request.cupMl,
            "history" to request.history.map { mapOf("fromUser" to it.isFromUser, "text" to it.text) },
            "message" to request.message,
        )
        return callFunction(CHAT_FUNCTION, payload, ::parseChat)
    }

    private fun contextPayload(request: NudgeRequest): Map<String, Any> = mapOf(
        "personaId" to request.personaId,
        "timeContext" to request.timeContext,
        "currentMl" to request.currentMl,
        "targetMl" to request.targetMl,
    )

    private suspend fun <T> callFunction(name: String, payload: Map<String, Any>, parse: (Map<*, *>) -> T): Result<T> {
        if (!isAvailable) return Result.failure(IllegalStateException("Firebase is not configured"))
        return try {
            ensureSignedIn()
            val result = FirebaseFunctions.getInstance(REGION).getHttpsCallable(name).call(payload).await()
            val data = requireNotNull(result.getData() as? Map<*, *>) { "Proxy response is not an object" }
            Result.success(parse(data))
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

    private fun parseNudge(map: Map<*, *>): NudgeContent {
        val nudge = NudgeContent(
            chatText = map["chatText"] as? String ?: "",
            quickReplyText = map["quickReplyText"] as? String ?: "",
            praiseText = map["praiseText"] as? String ?: "",
        )
        require(nudge.chatText.isNotBlank()) { "Proxy returned an empty chat text" }
        return nudge
    }

    private fun parseChat(map: Map<*, *>): ChatReply =
        parseChatReply(ChatReply(map["replyText"] as? String ?: "", (map["drankMl"] as? Number)?.toInt() ?: 0))

    private companion object {
        // Must match the region and names in backend/functions/src/index.ts.
        const val REGION = "asia-southeast1"
        const val NUDGE_FUNCTION = "generateNudge"
        const val CHAT_FUNCTION = "chatReply"
    }
}
