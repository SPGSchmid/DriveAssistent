package com.driveassistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * EmailDao
 * ════════
 * Room Data Access Object für [CachedEmail]-Einträge.
 */
@Dao
interface EmailDao {

    /**
     * Fügt eine Liste von E-Mails in den Cache ein.
     * Bei Konflikt (gleiche [CachedEmail.id]) wird der bestehende Eintrag ersetzt.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(emails: List<CachedEmail>)

    /**
     * Gibt alle gecachten ungelesenen E-Mails zurück,
     * sortiert nach Empfangszeitpunkt (neueste zuerst).
     */
    @Query("SELECT * FROM cached_emails WHERE isRead = 0 ORDER BY receivedDateTime DESC")
    suspend fun getUnreadEmails(): List<CachedEmail>

    /**
     * Löscht alle Cache-Einträge, die vor [timestampMs] gespeichert wurden.
     * Wird genutzt, um veraltete Einträge (> 24 h) zu entfernen.
     */
    @Query("DELETE FROM cached_emails WHERE cachedAt < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)
}
