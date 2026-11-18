package com.hydrachat.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WaterLog::class, ChatMessage::class, CachedNudge::class, QuestLog::class],
    version = 2,
    exportSchema = false,
)
abstract class HydraDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao
    abstract fun chatDao(): ChatDao
    abstract fun nudgeDao(): NudgeDao
    abstract fun questDao(): QuestDao

    companion object {
        fun create(context: Context): HydraDatabase =
            Room.databaseBuilder(context, HydraDatabase::class.java, "hydrachat.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds the flag that marks chat lines written by Gemini. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isFromAi INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
