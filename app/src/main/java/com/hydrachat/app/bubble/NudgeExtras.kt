package com.hydrachat.app.bubble

import android.content.Intent
import com.hydrachat.app.ai.NudgeContent

private const val EXTRA_CHAT_TEXT = "chat_text"
private const val EXTRA_QUICK_REPLY = "quick_reply"
private const val EXTRA_IS_FROM_AI = "is_from_ai"

/** Shared by the overlay service and the system bubble activity, which receive the nudge the same way. */
fun Intent.putNudge(nudge: NudgeContent): Intent = this
    .putExtra(EXTRA_CHAT_TEXT, nudge.chatText)
    .putExtra(EXTRA_QUICK_REPLY, nudge.quickReplyText)
    .putExtra(EXTRA_IS_FROM_AI, nudge.isFromAi)

fun Intent.toNudge(): NudgeContent? {
    val chatText = getStringExtra(EXTRA_CHAT_TEXT)
    if (chatText.isNullOrBlank()) return null
    return NudgeContent(
        chatText = chatText,
        quickReplyText = getStringExtra(EXTRA_QUICK_REPLY).orEmpty(),
        isFromAi = getBooleanExtra(EXTRA_IS_FROM_AI, false),
    )
}
