package com.driveassistant.data.model.calendar

/**
 * CalendarEvent-Datenmodelle
 * ══════════════════════════
 * Repräsentieren die Microsoft Graph API CalendarView-Antwort.
 *
 * Graph API Endpunkt: GET /me/calendarView
 * Dokumentation: https://learn.microsoft.com/en-us/graph/api/calendar-list-calendarview
 */

data class CalendarEventListResponse(
    val value: List<CalendarEvent>
)

data class CalendarEvent(
    val id: String,
    val subject: String?,
    val start: DateTimeTimeZone,
    val end: DateTimeTimeZone,
    val location: Location?,
    val organizer: Organizer?,
    val isAllDay: Boolean,
    val bodyPreview: String?
)

data class DateTimeTimeZone(
    val dateTime: String,
    val timeZone: String
)

data class Location(
    val displayName: String?
)

data class Organizer(
    val emailAddress: EmailAddressCalendar
)

data class EmailAddressCalendar(
    val name: String?,
    val address: String
)
