package com.driveassistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CachedCalendarEvent
 * ═══════════════════
 * Room-Entity für den lokalen Offline-Cache von Kalender-Terminen.
 *
 * Wird von [GraphCalendarService] befüllt, wenn Termine erfolgreich
 * von der Graph API abgerufen wurden. Bei Netzwerkfehler werden die
 * gecachten Daten als Fallback zurückgegeben.
 *
 * Tabelle: "cached_calendar_events"
 */
@Entity(tableName = "cached_calendar_events")
data class CachedCalendarEvent(
    @PrimaryKey val id: String,

    /** Terminbetreff, z.B. "Kundengespräch Müller GmbH" */
    val subject: String?,

    /** Startzeitpunkt als ISO-8601-String, z.B. "2024-01-15T14:00:00" */
    val startDateTime: String,

    /** Endzeitpunkt als ISO-8601-String */
    val endDateTime: String,

    /** Veranstaltungsort, z.B. "Musterstraße 1, 80331 München" */
    val locationName: String?,

    /** Name des Organisators */
    val organizerName: String?,

    /** true wenn Ganztags-Termin */
    val isAllDay: Boolean,

    /** Zeitstempel des letzten Cache-Schreibvorgangs (Epoch Milliseconds) */
    val cachedAt: Long = System.currentTimeMillis()
)
