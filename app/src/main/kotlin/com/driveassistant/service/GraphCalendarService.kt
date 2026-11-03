package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.api.GraphCalendarApi
import com.driveassistant.data.local.AppDatabase
import com.driveassistant.data.local.CachedCalendarEvent
import com.driveassistant.data.model.calendar.CalendarEvent
import com.driveassistant.data.model.calendar.CreateEventRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * GraphCalendarService
 * ════════════════════
 * Business-Logic-Schicht für alle Kalender-Operationen via Microsoft Graph API.
 *
 * Features:
 *  - Termine für heute / morgen / nächster Termin abrufen
 *  - Neuen Termin anlegen
 *  - Offline-Fallback: Bei Netzwerkfehler → Room-Cache zurückgeben
 *  - [buildSpeechText] wandelt Terminliste in deutschen Sprechtext um
 *
 * Sub-Task 2.1 – Kalender-Integration (Outlook Calendar via Graph API)
 */
class GraphCalendarService(private val context: Context) {

    companion object {
        private const val TAG = "GraphCalendarService"
        private val TIMEZONE = ZoneId.of("Europe/Berlin")
        private val API_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    private val api: GraphCalendarApi by lazy {
        GraphApiClient.retrofit.create(GraphCalendarApi::class.java)
    }

    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.calendarEventDao() }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Termine für heute (00:00 bis 23:59 Uhr Berliner Zeit) abrufen.
     * Bei Netzwerkfehler → Offline-Cache zurückgeben.
     */
    suspend fun getTodayEvents(): List<CalendarEvent> {
        val today = LocalDate.now(TIMEZONE)
        val start = today.atStartOfDay(TIMEZONE)
        val end   = today.atTime(23, 59, 59).atZone(TIMEZONE)
        return fetchEventsWithCache(start, end)
    }

    /**
     * Termine für morgen (00:00 bis 23:59 Uhr Berliner Zeit) abrufen.
     * Bei Netzwerkfehler → Offline-Cache zurückgeben.
     */
    suspend fun getTomorrowEvents(): List<CalendarEvent> {
        val tomorrow = LocalDate.now(TIMEZONE).plusDays(1)
        val start = tomorrow.atStartOfDay(TIMEZONE)
        val end   = tomorrow.atTime(23, 59, 59).atZone(TIMEZONE)
        return fetchEventsWithCache(start, end)
    }

    /**
     * Den nächsten Termin ab jetzt zurückgeben.
     * Sucht im Zeitraum jetzt bis jetzt + 24 Stunden.
     */
    suspend fun getNextEvent(): CalendarEvent? {
        val now   = ZonedDateTime.now(TIMEZONE)
        val until = now.plusHours(24)
        val events = fetchEventsWithCache(now, until)
        return events.firstOrNull()
    }

    /**
     * Neuen Termin anlegen.
     *
     * @return true bei Erfolg, false bei Fehler
     */
    suspend fun createEvent(request: CreateEventRequest): Boolean {
        return try {
            api.createEvent(request)
            Log.d(TAG, "Termin '${request.subject}' erfolgreich angelegt.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Anlegen des Termins '${request.subject}'", e)
            false
        }
    }

    // ── Sprachausgabe ────────────────────────────────────────────────────────

    /**
     * Wandelt eine Terminliste in einen vorlesbaren deutschen Text um.
     *
     * - 0 Termine → "Du hast heute keine weiteren Termine."
     * - 1 Termin  → "Du hast heute noch einen Termin: [Betreff] um [Uhrzeit] [bei Ort]."
     * - N Termine → "Du hast heute noch N Termine. Erster Termin: ..."
     */
    fun buildSpeechText(events: List<CalendarEvent>): String {
        if (events.isEmpty()) {
            return "Du hast heute keine weiteren Termine."
        }

        return buildString {
            if (events.size == 1) {
                val e = events.first()
                append("Du hast heute noch einen Termin: ")
                append(e.subject ?: "Ohne Betreff")
                val time = parseTime(e.start.dateTime)
                if (time != null) append(" um $time Uhr")
                val location = e.location?.displayName
                if (!location.isNullOrBlank()) append(" bei $location")
                append(".")
            } else {
                append("Du hast heute noch ${events.size} Termine.")
                events.forEachIndexed { index, e ->
                    val ordinal = when (index) {
                        0    -> "Erster"
                        1    -> "Zweiter"
                        2    -> "Dritter"
                        3    -> "Vierter"
                        4    -> "Fünfter"
                        else -> "${index + 1}."
                    }
                    append(" $ordinal Termin: ")
                    append(e.subject ?: "Ohne Betreff")
                    val time = parseTime(e.start.dateTime)
                    if (time != null) append(" um $time Uhr")
                    val location = e.location?.displayName
                    if (!location.isNullOrBlank()) append(" bei $location")
                    append(".")
                }
            }
        }
    }

    // ── Private Hilfsmethoden ────────────────────────────────────────────────

    /**
     * Termine abrufen mit Offline-Fallback.
     *
     * 1. Versucht API-Call
     * 2. Bei Erfolg: Cache aktualisieren, Ergebnis zurückgeben
     * 3. Bei Netzwerkfehler: gecachte Einträge aus Room zurückgeben
     */
    private suspend fun fetchEventsWithCache(
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<CalendarEvent> {
        val startStr = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val endStr   = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        return try {
            val response = api.getCalendarView(
                startDateTime = startStr,
                endDateTime   = endStr
            )
            val events = response.value

            // Cache aktualisieren
            val cached = events.map { it.toCached() }
            dao.insertAll(cached)

            // Alten Cache aufräumen (älter als 7 Tage)
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            dao.deleteOlderThan(sevenDaysAgo)

            events
        } catch (e: Exception) {
            Log.w(TAG, "Netzwerkfehler – lade Termine aus lokalem Cache.", e)

            // Fallback: ISO-String-Vergleich über cachedAt-Range
            val startIso = startStr
            val endIso   = endStr
            dao.getEventsForRange(startIso, endIso).map { it.toCalendarEvent() }
        }
    }

    /** Parst ISO-8601-Datetime-String und gibt HH:mm zurück, oder null bei Fehler. */
    private fun parseTime(dateTimeStr: String): String? {
        return try {
            val ldt = LocalDateTime.parse(dateTimeStr, API_FORMATTER)
            ldt.format(TIME_FORMATTER)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // ── Konvertierungsfunktionen ─────────────────────────────────────────────

    private fun CalendarEvent.toCached() = CachedCalendarEvent(
        id            = id,
        subject       = subject,
        startDateTime = start.dateTime,
        endDateTime   = end.dateTime,
        locationName  = location?.displayName,
        organizerName = organizer?.emailAddress?.name,
        isAllDay      = isAllDay,
        cachedAt      = System.currentTimeMillis()
    )

    private fun CachedCalendarEvent.toCalendarEvent() = CalendarEvent(
        id           = id,
        subject      = subject,
        start        = com.driveassistant.data.model.calendar.DateTimeTimeZone(startDateTime, "Europe/Berlin"),
        end          = com.driveassistant.data.model.calendar.DateTimeTimeZone(endDateTime, "Europe/Berlin"),
        location     = locationName?.let { com.driveassistant.data.model.calendar.Location(it) },
        organizer    = null,
        isAllDay     = isAllDay,
        bodyPreview  = null
    )
}
