package com.driveassistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * AssistantIntent
 * ═══════════════
 * Das von GPT-4 zurückgegebene, geparste Intent-Modell.
 *
 * GPT-4 antwortet IMMER als valides JSON-Objekt in diesem Format:
 * {
 *   "intent":  "READ_EMAILS",
 *   "params":  { "email_id": "abc123", "reply_text": "Danke für die Nachricht." },
 *   "speech":  "Ich lese jetzt deine ungelesenen E-Mails vor."
 * }
 *
 * Verfügbare Intents:
 *  - READ_EMAILS       – Ungelesene E-Mails vorlesen
 *  - REPLY_EMAIL       – E-Mail beantworten        (params: email_id, reply_text)
 *  - COMPOSE_EMAIL     – Neue E-Mail senden         (params: to_name, subject, body)
 *  - GET_CALENDAR      – Termine abfragen           (params: date)
 *  - CREATE_EVENT      – Termin erstellen           (params: title, date, time, location, attendee)
 *  - NAVIGATE          – Navigation starten         (params: address | contact_name)
 *  - PLAY_MUSIC        – Musik abspielen            (params: query, type)
 *  - TEAMS_MESSAGE     – Teams-Nachricht senden     (params: to_name, message)
 *  - TEAMS_CALL        – Teams-Anruf starten        (params: to_name, to_email)
 *  - HUBSPOT_LOOKUP    – HubSpot-Kontakt nachschlagen (params: contact_name)
 *  - CREATE_NOTE       – HubSpot-Notiz erstellen    (params: contact_name, note_text)
 *  - IMPORT_PLAUD      – PLAUD-Transkript importieren
 *  - GENERAL_RESPONSE  – Allgemeine Antwort ohne Aktion
 */
data class AssistantIntent(
    @SerializedName("intent")
    val intent: String,

    @SerializedName("params")
    val params: Map<String, String>,

    @SerializedName("speech")
    val speech: String,
)
