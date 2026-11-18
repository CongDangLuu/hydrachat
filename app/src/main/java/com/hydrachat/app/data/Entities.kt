package com.hydrachat.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amountMl: Int,
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val isFromUser: Boolean,
    val text: String,
    val quest: String? = null,
    @ColumnInfo(defaultValue = "0") val isFromAi: Boolean = false,
)

/** A Gemini reply fetched ahead of the reminder so the bubble can show instantly. */
@Entity(tableName = "cached_nudges")
data class CachedNudge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaId: String,
    val chatText: String,
    val quickReplyText: String,
    val miniQuest: String,
    val createdAt: Long,
)

@Entity(tableName = "quest_logs")
data class QuestLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val questText: String,
)
