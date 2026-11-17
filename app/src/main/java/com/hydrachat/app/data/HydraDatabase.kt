package com.hydrachat.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WaterLog::class, ChatMessage::class, CachedNudge::class, QuestLog::class],
    version = 1,
    exportSchema = false,
)
abstract class HydraDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao
    abstract fun chatDao(): ChatDao
    abstract fun nudgeDao(): NudgeDao
    abstract fun questDao(): QuestDao

    companion object {
        fun create(context: Context): HydraDatabase =
            Room.databaseBuilder(context, HydraDatabase::class.java, "hydrachat.db").build()
    }
}
