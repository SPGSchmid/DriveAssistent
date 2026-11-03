package com.driveassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * AppDatabase
 * ═══════════
 * Zentrale Room-Datenbank der DriveAssistant-App.
 *
 * Version 1: CachedEmail (Sub-Task 1.5)
 * Version 2: + CachedCalendarEvent (Sub-Task 2.1)
 * Version 3: + PlaudImportRecord (Sub-Task 3.2)
 *
 * [fallbackToDestructiveMigration] wird genutzt, da die App primär als Cache
 * fungiert – alle Daten können jederzeit neu von der API geladen werden.
 */
@Database(
    entities = [
        CachedEmail::class,
        CachedCalendarEvent::class,
        PlaudImportRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun emailDao(): EmailDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun plaudImportDao(): PlaudImportDao

    companion object {

        private const val DB_NAME = "drive_assistant.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
