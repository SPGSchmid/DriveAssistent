package com.driveassistant.data.model.calendar

/**
 * CreateEventRequest
 * ══════════════════
 * Request-Body für POST /me/events (neuen Kalender-Termin anlegen).
 *
 * Dokumentation: https://learn.microsoft.com/en-us/graph/api/user-post-events
 */

data class CreateEventRequest(
    val subject: String,
    val start: DateTimeTimeZone,
    val end: DateTimeTimeZone,
    val location: Location? = null,
    val attendees: List<Attendee>? = null,
    val body: MessageBodyCalendar? = null
)

data class Attendee(
    val emailAddress: EmailAddressCalendar,
    val type: String = "required"
)

data class MessageBodyCalendar(
    val contentType: String = "Text",
    val content: String = ""
)
