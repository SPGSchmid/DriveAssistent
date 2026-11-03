package com.driveassistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * CalendarEventDao
 * ════════════════
 * Room-DAO für den lokalen Kalender-Offline-Cache.
 *
 * Wird von [GraphCalendarService] genutzt, um Termine zu speichern
 * und bei fehlender Netzwerkverbindung zurückzulesen.
 */
@Dao
interface CalendarEventDao {

    /**
     * Alle Termine einfügen oder ersetzen (bei gleicher ID).
     * Wird nach jedem erfolgreichen Graph-API-Abruf aufgerufen.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CachedCalendarEvent>)

    /**
     * Termine zurückgeben, deren Startzeitpunkt in [startMs]..[endMs] liegt.
     *
     * @param startMs  Beginn des Zeitraums als Epoch-Milliseconds
     * @param endMs    Ende des Zeitraums als Epoch-Milliseconds
     *
     * Hinweis: [CachedCalendarEvent.startDateTime] wird als ISO-String
     * gespeichert; der Vergleich erfolgt über den [cachedAt]-Timestamp.
     * Für den Range-Query wird stattdessen ein String-Vergleich genutzt
     * (ISO-8601-Strings sind lexikografisch sortierbar).
     */
    @Query("""
        SELECT * FROM cached_calendar_events
        WHERE startDateTime >= :startIso AND startDateTime <= :endIso
        ORDER BY startDateTime ASC
    """)
    suspend fun getEventsForRange(startIso: String, endIso: String): List<CachedCalendarEvent>

    /**
     * Veraltete Cache-Einträge löschen.
     *
     * @param timestampMs Alle Einträge, die vor diesem Zeitpunkt gecacht wurden,
     *                    werden gelöscht (Epoch-Milliseconds).
     */
    @Query("DELETE FROM cached_calendar_events WHERE cachedAt < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)
}
