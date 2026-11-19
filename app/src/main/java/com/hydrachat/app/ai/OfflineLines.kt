package com.hydrachat.app.ai

import android.content.Context
import com.hydrachat.app.domain.Persona
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shown next to a reminder line so the user can tell Gemini lines from the offline library. */
const val AI_SOURCE_LABEL = "✨ Tạo bởi Gemini"

@Serializable
data class NudgeContent(
    val chatText: String,
    val quickReplyText: String,
    /** Gemini's reply for when the user confirms the drink; offline lines leave it empty. */
    val praiseText: String = "",
    val isFromAi: Boolean = false,
)

@Serializable
private data class PersonaLines(val lines: List<String>, val replies: List<String> = emptyList())

@Serializable
private data class OfflineLibrary(
    val personas: Map<String, PersonaLines>,
    val packs: Map<String, PersonaLines> = emptyMap(),
    val praises: Map<String, List<String>> = emptyMap(),
)

/** Bundled fallback lines used when Gemini is unreachable, out of quota, or not configured. */
class OfflineLines(private val context: Context) {
    private val library: OfflineLibrary by lazy {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<OfflineLibrary>(raw)
    }

    fun pick(persona: Persona, activePacks: Set<String>): NudgeContent {
        val own = library.personas[persona.id]
        val packLines = activePacks.flatMap { library.packs[it]?.lines.orEmpty() }
        val pool = own?.lines.orEmpty() + packLines
        return NudgeContent(
            chatText = pool.randomOrNull() ?: DEFAULT_LINE,
            quickReplyText = own?.replies?.randomOrNull() ?: DEFAULT_REPLY,
        )
    }

    fun praise(persona: Persona): String = library.praises[persona.id]?.randomOrNull() ?: DEFAULT_PRAISE

    private companion object {
        const val ASSET_NAME = "offline_lines.json"
        const val DEFAULT_LINE = "Đến giờ uống nước rồi nè! 💧"
        const val DEFAULT_REPLY = "Uống liền 🥤"
        const val DEFAULT_PRAISE = "Tuyệt vời! 👏"
    }
}
