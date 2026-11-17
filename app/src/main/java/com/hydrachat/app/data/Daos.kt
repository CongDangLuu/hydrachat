package com.hydrachat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(log: WaterLog)

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE timestamp >= :from")
    suspend fun sumSince(from: Long): Int

    @Query("SELECT * FROM water_logs WHERE timestamp >= :from ORDER BY timestamp")
    fun observeSince(from: Long): Flow<List<WaterLog>>
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE timestamp >= :from ORDER BY timestamp, id")
    fun observeSince(from: Long): Flow<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface NudgeDao {
    @Insert
    suspend fun insert(nudge: CachedNudge)

    @Query(
        "SELECT * FROM cached_nudges WHERE personaId = :personaId AND createdAt >= :minCreatedAt " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun latestFor(personaId: String, minCreatedAt: Long): CachedNudge?

    @Query("DELETE FROM cached_nudges WHERE id = :id OR createdAt < :minCreatedAt")
    suspend fun consume(id: Long, minCreatedAt: Long)
}

@Dao
interface QuestDao {
    @Insert
    suspend fun insert(log: QuestLog)

    @Query("SELECT COUNT(*) FROM quest_logs WHERE timestamp >= :from")
    fun observeCountSince(from: Long): Flow<Int>
}
