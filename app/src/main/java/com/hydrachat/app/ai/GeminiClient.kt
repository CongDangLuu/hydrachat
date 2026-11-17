package com.hydrachat.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class NudgeRequest(
    val personaRole: String,
    val timeContext: String,
    val currentMl: Int,
    val targetMl: Int,
)

/** Calls the Gemini REST API with a JSON response schema (structured output). */
class GeminiClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateNudge(apiKey: String, request: NudgeRequest): Result<NudgeContent> =
        withContext(Dispatchers.IO) {
            try {
                val responseBody = post(apiKey, buildBody(request).toString())
                Result.success(parse(responseBody))
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: SerializationException) {
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
                Result.failure(e)
            }
        }

    private fun post(apiKey: String, body: String): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Header instead of a query parameter keeps the key out of URLs and logs.
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Gemini request failed with HTTP $status")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(responseBody: String): NudgeContent {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: throw SerializationException("Gemini response has no text part")
        val nudge = json.decodeFromString<NudgeContent>(text)
        require(nudge.chatText.isNotBlank()) { "Gemini returned an empty chat text" }
        return nudge
    }

    private fun buildBody(request: NudgeRequest): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", SYSTEM_INSTRUCTION) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", userPrompt(request)) } }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", TEMPERATURE)
            put("responseMimeType", "application/json")
            putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
            putJsonObject("responseSchema") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    stringField("chatText", "Câu nhắn nhắc uống nước ngắn gọn, đúng tính cách, có emoji")
                    stringField("quickReplyText", "Nội dung nút bấm xác nhận đã uống, tối đa 4 từ")
                    stringField("miniQuest", "Một hành động giãn cơ/nghỉ mắt 10 giây cho dân văn phòng")
                }
                putJsonArray("required") {
                    add("chatText")
                    add("quickReplyText")
                    add("miniQuest")
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.stringField(name: String, description: String) {
        putJsonObject(name) {
            put("type", "STRING")
            put("description", description)
        }
    }

    private fun userPrompt(request: NudgeRequest): String =
        "Vai diễn: ${request.personaRole} | Thời điểm: ${request.timeContext} | " +
            "Tiến độ: ${request.currentMl} / ${request.targetMl} ml. " +
            "Hãy nhắn 1 tin nhắc uống nước kèm nhiệm vụ vận động nhỏ."

    private companion object {
        const val MODEL = "gemini-2.5-flash"
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        const val TIMEOUT_MS = 15_000
        const val TEMPERATURE = 0.9
        const val SYSTEM_INSTRUCTION =
            "Bạn là người bạn ảo nhắc uống nước qua bong bóng chat. Viết tin nhắn tiếng Việt tự nhiên, " +
                "ngắn gọn dưới 25 từ, đúng vai diễn, bắt trend nhẹ nhàng. Không chào hỏi rườm rà. " +
                "Luôn trả về định dạng JSON theo đúng schema quy định."
    }
}
