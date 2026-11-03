package com.driveassistant.api

import com.driveassistant.data.model.calendar.CalendarEvent
import com.driveassistant.data.model.calendar.CalendarEventListResponse
import com.driveassistant.data.model.calendar.CreateEventRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * GraphCalendarApi
 * ════════════════
 * Retrofit-Interface für Microsoft Graph Calendar API.
 *
 * Base URL: https://graph.microsoft.com/v1.0/ (via [GraphApiClient])
 *
 * Endpunkte:
 *  - GET  /me/calendarView  – Termine in einem Zeitraum abrufen
 *  - POST /me/events         – Neuen Termin anlegen
 *
 * Dokumentation: https://learn.microsoft.com/en-us/graph/api/resources/calendar
 */
interface GraphCalendarApi {

    /**
     * Termine im angegebenen Zeitraum abrufen (CalendarView berücksichtigt
     * wiederkehrende Termine korrekt – besser als /me/events mit Filter).
     *
     * @param startDateTime ISO 8601, z.B. "2024-01-01T00:00:00"
     * @param endDateTime   ISO 8601, z.B. "2024-01-01T23:59:59"
     * @param orderBy       OData-Sortierung (Standard: nach Startzeit aufsteigend)
     * @param select        Kommagetrennte Felder (reduziert Payload)
     * @param top           Maximale Anzahl Ergebnisse (Standard: 20)
     */
    @GET("me/calendarView")
    suspend fun getCalendarView(
        @Query("startDateTime")  startDateTime: String,
        @Query("endDateTime")    endDateTime: String,
        @Query("\$orderby")      orderBy: String = "start/dateTime asc",
        @Query("\$select")       select: String = "id,subject,start,end,location,organizer,isAllDay,bodyPreview",
        @Query("\$top")          top: Int = 20
    ): CalendarEventListResponse

    /**
     * Neuen Termin im Kalender des angemeldeten Nutzers anlegen.
     *
     * @param event [CreateEventRequest] mit Titel, Zeitraum, Ort und Teilnehmern
     * @return Das neu erstellte [CalendarEvent] mit server-generierter ID
     */
    @POST("me/events")
    suspend fun createEvent(@Body event: CreateEventRequest): CalendarEvent
}
