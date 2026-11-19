package com.hydrachat.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WaterLog::class, ChatMessage::class, CachedNudge::class],
    version = 3,
    exportSchema = false,
)
abstract class HydraDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao
    abstract fun chatDao(): ChatDao
    abstract fun nudgeDao(): NudgeDao

    companion object {
        fun create(context: Context): HydraDatabase =
            Room.databaseBuilder(context, HydraDatabase::class.java, "hydrachat.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** Adds the flag that marks chat lines written by Gemini. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isFromAi INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Drops the mini-quest columns and log; SQLite on API 26 has no DROP COLUMN, so tables are rebuilt. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS quest_logs")
                rebuildTable(
                    db,
                    "chat_messages",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, " +
                        "isFromUser INTEGER NOT NULL, text TEXT NOT NULL, isFromAi INTEGER NOT NULL DEFAULT 0",
                    "id, timestamp, isFromUser, text, isFromAi",
                )
                rebuildTable(
                    db,
                    "cached_nudges",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personaId TEXT NOT NULL, " +
                        "chatText TEXT NOT NULL, quickReplyText TEXT NOT NULL, createdAt INTEGER NOT NULL",
                    "id, personaId, chatText, quickReplyText, createdAt",
                )
            }
        }

        private fun rebuildTable(db: SupportSQLiteDatabase, table: String, columnDefinitions: String, columns: String) {
            db.execSQL("CREATE TABLE ${table}_new ($columnDefinitions)")
            db.execSQL("INSERT INTO ${table}_new ($columns) SELECT $columns FROM $table")
            db.execSQL("DROP TABLE $table")
            db.execSQL("ALTER TABLE ${table}_new RENAME TO $table")
        }
    }
}
