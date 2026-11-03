package com.driveassistant.service

import android.util.Log
import com.driveassistant.data.model.Message

/**
 * SessionContext
 * ══════════════
 * Hält das Konversationsgedächtnis für GPT-4.
 *
 * Aufbau:
 *  - Erste Nachricht ist immer der System-Prompt (definiert Rolle + JSON-Format)
 *  - Anschließend folgen abwechselnd user/assistant Nachrichten
 *  - Maximal [MAX_HISTORY_MESSAGES] Nutzer-/Assistenten-Nachrichten werden gespeichert
 *    (älteste werden entfernt, System-Prompt bleibt immer erhalten)
 *
 * Verwendung:
 *   val ctx = SessionContext()
 *   ctx.addUserMessage("Lies meine Mails vor")
 *   val msgs = ctx.getMessages()   // System-Prompt + Verlauf → an GPT-4 schicken
 *   ctx.addAssistantMessage(gptJson)
 */
class SessionContext {

    companion object {
        private const val TAG = "SessionContext"

        /** Maximale Anzahl Nutzer- + Assistenten-Nachrichten (ohne System-Prompt). */
        private const val MAX_HISTORY_MESSAGES = 10

        // ── System-Prompt ────────────────────────────────────────────────────
        // Definiert die Rolle des Assistenten, das JSON-Antwortformat
        // und alle verfügbaren Intents.
        private val SYSTEM_PROMPT = Message(
            role = "system",
            content = """
Du bist ein KI-Assistent für einen Außendienstmitarbeiter im Auto. Du antwortest IMMER als valides JSON-Objekt in folgendem Format:
{"intent": "...", "params": {"key": "value"}, "speech": "..."}

Verfügbare Intents:
- READ_EMAILS: Ungelesene E-Mails vorlesen (params: leer)
- REPLY_EMAIL: E-Mail beantworten (params: email_id, reply_text)
- COMPOSE_EMAIL: Neue E-Mail senden (params: to_name, subject, body)
- GET_CALENDAR: Termine abfragen (params: date "today"/"tomorrow"/"next")
- CREATE_EVENT: Termin erstellen (params: title, date, time, location, attendee)
- NAVIGATE: Navigation starten (params: address oder contact_name)
- PLAY_MUSIC: Musik abspielen oder Wiedergabe steuern (params: query [Suchbegriff oder Steuerbefehl "pause"/"weiter"/"nächstes"/"zurück"], type "playlist"/"artist"/"album" [optional])
- TEAMS_MESSAGE: Teams-Nachricht senden (params: to_name, message)
- TEAMS_CALL: Teams-Anruf starten (params: to_name, to_email)
- HUBSPOT_LOOKUP: Kontakt in HubSpot nachschlagen (params: contact_name)
- CREATE_NOTE: HubSpot-Notiz erstellen (params: contact_name, note_text)
- IMPORT_PLAUD: Letztes PLAUD-Transkript importieren (params: leer)
- GENERAL_RESPONSE: Allgemeine Antwort ohne Aktion (params: leer)

Die speech-Antwort ist IMMER auf Deutsch. Sei präzise und kurz (max. 2 Sätze für speech).
            """.trimIndent(),
        )
    }

    private val history: MutableList<Message> = mutableListOf()

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Fügt eine Nutzer-Nachricht zum Verlauf hinzu.
     * Falls der Verlauf [MAX_HISTORY_MESSAGES] überschreitet,
     * wird das älteste Paar (user + assistant) entfernt.
     */
    fun addUserMessage(text: String) {
        history.add(Message(role = "user", content = text))
        trimHistoryIfNeeded()
        Log.d(TAG, "User-Nachricht hinzugefügt. Verlauf: ${history.size} Nachrichten.")
    }

    /**
     * Fügt eine Assistenten-Antwort zum Verlauf hinzu.
     */
    fun addAssistantMessage(text: String) {
        history.add(Message(role = "assistant", content = text))
        Log.d(TAG, "Assistant-Nachricht hinzugefügt. Verlauf: ${history.size} Nachrichten.")
    }

    /**
     * Gibt die vollständige Nachrichtenliste zurück:
     * [System-Prompt] + [Verlauf].
     * Diese Liste wird direkt als `messages` an GPT-4 übergeben.
     */
    fun getMessages(): List<Message> = listOf(SYSTEM_PROMPT) + history

    /**
     * Löscht den gesamten Gesprächsverlauf (ohne System-Prompt).
     * Nützlich für eine neue Konversationssitzung.
     */
    fun clear() {
        history.clear()
        Log.d(TAG, "Gesprächsverlauf gelöscht.")
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Kürzt den Verlauf wenn er [MAX_HISTORY_MESSAGES] übersteigt.
     * Entfernt immer das älteste Element (FIFO), damit der Verlauf
     * zeitlich kohärent bleibt.
     */
    private fun trimHistoryIfNeeded() {
        while (history.size > MAX_HISTORY_MESSAGES) {
            history.removeAt(0)
            Log.d(TAG, "Älteste Nachricht entfernt. Verlauf jetzt: ${history.size}")
        }
    }
}
