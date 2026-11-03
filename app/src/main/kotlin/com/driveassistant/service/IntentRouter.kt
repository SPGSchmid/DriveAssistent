package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.data.model.AssistantIntent
import com.driveassistant.data.model.calendar.Attendee
import com.driveassistant.data.model.calendar.CreateEventRequest
import com.driveassistant.data.model.calendar.DateTimeTimeZone
import com.driveassistant.data.model.calendar.EmailAddressCalendar
import com.driveassistant.data.model.calendar.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * IntentResultCallback
 * ════════════════════
 * Callback-Interface für das Ergebnis des Intent-Routings.
 *
 * [onSpeechReady] wird aufgerufen wenn die Aktion abgeschlossen ist
 * und ein Text für die TTS-Ausgabe bereitsteht.
 * [onError] wird aufgerufen wenn die Aktion fehlschlägt.
 */
interface IntentResultCallback {
    fun onSpeechReady(text: String)
    fun onError(message: String)
}

/**
 * IntentRouter
 * ════════════
 * Empfängt einen [AssistantIntent] von GPT-4 und routet zur entsprechenden Aktion.
 *
 * Phase-1-Status:
 *  ✅ READ_EMAILS       – Implementiert (Platzhalter für Graph-API in Sub-Task 1.5)
 *  ✅ REPLY_EMAIL       – Implementiert (Platzhalter für Graph-API in Sub-Task 1.5)
 *  ✅ COMPOSE_EMAIL     – Implementiert (Platzhalter für Graph-API in Sub-Task 1.5)
 *  ✅ GENERAL_RESPONSE  – Implementiert (spricht direkt die speech-Eigenschaft)
 *  ✅ GET_CALENDAR      – Implementiert (Sub-Task 2.1)
 *  ✅ CREATE_EVENT      – Implementiert (Sub-Task 2.1)
 *  ✅ NAVIGATE          – Implementiert (Sub-Task 2.1 / 4.2)
 *  ✅ TEAMS_MESSAGE     – Implementiert (Sub-Task 2.2)
 *  ✅ TEAMS_CALL        – Implementiert (Sub-Task 2.2)
 *  ✅ HUBSPOT_LOOKUP    – Implementiert (Sub-Task 3.1)
 *  ✅ CREATE_NOTE       – Implementiert (Sub-Task 3.1)
 *  ✅ IMPORT_PLAUD      – Implementiert (Sub-Task 3.2)
 *  ✅ PLAY_MUSIC        – Implementiert (Sub-Task 4.1)
 */
class IntentRouter(private val context: Context) {

    companion object {
        private const val TAG = "IntentRouter"

        // Intent-Konstanten
        const val INTENT_READ_EMAILS      = "READ_EMAILS"
        const val INTENT_REPLY_EMAIL      = "REPLY_EMAIL"
        const val INTENT_COMPOSE_EMAIL    = "COMPOSE_EMAIL"
        const val INTENT_GET_CALENDAR     = "GET_CALENDAR"
        const val INTENT_CREATE_EVENT     = "CREATE_EVENT"
        const val INTENT_NAVIGATE         = "NAVIGATE"
        const val INTENT_PLAY_MUSIC       = "PLAY_MUSIC"
        const val INTENT_TEAMS_MESSAGE    = "TEAMS_MESSAGE"
        const val INTENT_TEAMS_CALL       = "TEAMS_CALL"
        const val INTENT_HUBSPOT_LOOKUP   = "HUBSPOT_LOOKUP"
        const val INTENT_CREATE_NOTE      = "CREATE_NOTE"
        const val INTENT_IMPORT_PLAUD     = "IMPORT_PLAUD"
        const val INTENT_GENERAL_RESPONSE = "GENERAL_RESPONSE"
    }

    /**
     * Routet [intent] zur entsprechenden Aktion.
     * Das Ergebnis wird asynchron über [callback] zurückgegeben.
     *
     * Bei unbekannten Intents wird [intent.speech] direkt vorgelesen
     * (Graceful Degradation – GPT-4 hat immer eine speech-Antwort).
     */
    fun route(intent: AssistantIntent, callback: IntentResultCallback) {
        Log.d(TAG, "Routing Intent: ${intent.intent}")
        when (intent.intent) {
            INTENT_READ_EMAILS      -> handleReadEmails(intent, callback)
            INTENT_REPLY_EMAIL      -> handleReplyEmail(intent, callback)
            INTENT_COMPOSE_EMAIL    -> handleComposeEmail(intent, callback)
            INTENT_GENERAL_RESPONSE -> callback.onSpeechReady(intent.speech)

            // ── Phase 2 ───────────────────────────────────────────────────
            INTENT_GET_CALENDAR     -> handleGetCalendar(intent, callback)
            INTENT_CREATE_EVENT     -> handleCreateEvent(intent, callback)
            INTENT_TEAMS_MESSAGE    -> handleTeamsMessage(intent, callback)
            INTENT_TEAMS_CALL       -> handleTeamsCall(intent, callback)

            // ── Phase 3 ───────────────────────────────────────────────────
            INTENT_HUBSPOT_LOOKUP   -> handleHubSpotLookup(intent, callback)
            INTENT_CREATE_NOTE      -> handleCreateNote(intent, callback)
            INTENT_IMPORT_PLAUD     -> handleImportPlaud(callback)

            // ── Phase 4 ───────────────────────────────────────────────────
            INTENT_NAVIGATE         -> handleNavigate(intent, callback)
            INTENT_PLAY_MUSIC       -> handlePlayMusic(intent, callback)

            else -> {
                Log.w(TAG, "Unbekannter Intent: '${intent.intent}' – speech wird direkt ausgegeben.")
                callback.onSpeechReady(intent.speech)
            }
        }
    }

    // ── Service-Instanzen ────────────────────────────────────────────────────

    private val spotifyService  = SpotifyService
    private val mailService     by lazy { GraphMailService(context) }
    private val calendarService by lazy { GraphCalendarService(context) }
    private val teamsService    by lazy { GraphTeamsService(context) }
    private val hubSpotService  by lazy { HubSpotService(context) }
    private val plaudImportService by lazy { PlaudImportService(context) }
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // ── Phase-3-Handler ──────────────────────────────────────────────────────

    /**
     * HUBSPOT_LOOKUP: Kontakt in HubSpot suchen und Daten vorlesen.
     *
     * Erwartet params: contact_name (Vor- und/oder Nachname des gesuchten Kontakts)
     * Der gefundene Kontakt wird gecacht für Folgebefehle (z.B. "erstelle eine Notiz").
     */
    private fun handleHubSpotLookup(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val contactName = intent.params["contact_name"]

        if (contactName == null) {
            Log.w(TAG, "HUBSPOT_LOOKUP: fehlender contact_name. params=${intent.params}")
            callback.onError("Ich konnte den Kontakt nicht suchen. Der Name fehlt.")
            return
        }

        ioScope.launch {
            val contact = hubSpotService.searchContact(contactName)
            if (contact != null) {
                callback.onSpeechReady(hubSpotService.getContactSpeechText(contact))
            } else {
                callback.onSpeechReady("Ich konnte keinen Kontakt mit dem Namen $contactName in HubSpot finden.")
            }
        }
    }

    /**
     * CREATE_NOTE: Notiz in HubSpot anlegen.
     *
     * Erwartet params:
     *  - note_text  – Text der Notiz
     *  - contact_name (optional) – falls vorhanden, wird Kontakt neu gesucht;
     *    sonst wird der zuletzt gecachte Kontakt aus [hubSpotService] verwendet.
     */
    private fun handleCreateNote(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val noteText = intent.params["note_text"]

        if (noteText == null) {
            Log.w(TAG, "CREATE_NOTE: fehlender note_text. params=${intent.params}")
            callback.onError("Ich konnte die Notiz nicht erstellen. Der Notiztext fehlt.")
            return
        }

        ioScope.launch {
            // Kontakt ermitteln: explizit übergeben oder letzten gecachten nutzen
            val contactName = intent.params["contact_name"]
            val contact = if (!contactName.isNullOrBlank()) {
                hubSpotService.searchContact(contactName)
            } else {
                hubSpotService.getLastFoundContact()
            }

            if (contact == null) {
                Log.w(TAG, "CREATE_NOTE: kein Kontakt gefunden. params=${intent.params}")
                callback.onError("Ich konnte keinen Kontakt finden, dem ich die Notiz zuordnen soll. Bitte nenne den Kontaktnamen.")
                return@launch
            }

            val success = hubSpotService.createNote(contact.id, noteText)
            if (success) {
                val name = listOfNotNull(
                    contact.properties.firstname,
                    contact.properties.lastname
                ).joinToString(" ").ifBlank { "dem Kontakt" }
                callback.onSpeechReady("Notiz wurde bei $name in HubSpot gespeichert.")
            } else {
                callback.onError("Die Notiz konnte leider nicht gespeichert werden. Bitte prüfe deine Internetverbindung.")
            }
        }
    }

    /**
     * IMPORT_PLAUD: PLAUD-Verzeichnisse nach neuen Transkripten scannen
     * und gefundene Dateien via Groq analysieren und in HubSpot importieren.
     *
     * TTS-Rückmeldung je nach Ergebnis:
     *  - "Ich habe X neue PLAUD-Transkripte importiert und in HubSpot gespeichert."
     *  - "Keine neuen Transkripte gefunden."
     */
    private fun handleImportPlaud(callback: IntentResultCallback) {
        ioScope.launch {
            val count = plaudImportService.scanAndImport()
            val speech = if (count > 0) {
                "Ich habe $count ${if (count == 1) "neues PLAUD-Transkript" else "neue PLAUD-Transkripte"} importiert und in HubSpot gespeichert."
            } else {
                "Keine neuen Transkripte gefunden."
            }
            callback.onSpeechReady(speech)
        }
    }

    // ── Phase-2-Handler ──────────────────────────────────────────────────────

    /**
     * TEAMS_MESSAGE: Nachricht an eine Person auf Teams senden.
     *
     * Erwartet params: to_name (Anzeigename des Empfängers), message (Nachrichtentext)
     */
    private fun handleTeamsMessage(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val toName  = intent.params["to_name"]
        val message = intent.params["message"]

        if (toName == null || message == null) {
            Log.w(TAG, "TEAMS_MESSAGE: fehlende Parameter. params=${intent.params}")
            callback.onError("Ich konnte die Teams-Nachricht nicht senden. Empfänger oder Nachrichtentext fehlen.")
            return
        }

        ioScope.launch {
            val chat = teamsService.findChatWithPerson(toName)

            if (chat == null) {
                Log.w(TAG, "TEAMS_MESSAGE: kein Chat gefunden für '$toName'")
                callback.onSpeechReady("Ich konnte keinen Chat mit $toName finden. Bitte stelle sicher, dass du bereits mit dieser Person auf Teams geschrieben hast.")
                return@launch
            }

            val success = teamsService.sendMessage(chat.id, message)
            if (success) {
                callback.onSpeechReady("Nachricht an $toName wurde gesendet.")
            } else {
                callback.onError("Die Nachricht konnte leider nicht gesendet werden. Bitte prüfe deine Internetverbindung.")
            }
        }
    }

    /**
     * TEAMS_CALL: Teams-Anruf mit einer Person starten.
     *
     * Erwartet params: to_email (bevorzugt) oder to_name (Fallback via Chat-Cache)
     * TTS wird vor dem Intent ausgelöst, da die App danach den Fokus verliert.
     */
    private fun handleTeamsCall(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val toEmail = intent.params["to_email"]
        val toName  = intent.params["to_name"] ?: "deinem Kontakt"

        if (toEmail != null) {
            // E-Mail direkt vorhanden → sofort Anruf starten
            callback.onSpeechReady("Ich starte den Anruf mit $toName.")
            TeamsCallService.startCall(context, toEmail, toName)
            return
        }

        // Kein to_email → E-Mail aus Chat-Cache ermitteln
        ioScope.launch {
            val chat = teamsService.findChatWithPerson(toName)
            val emailFromCache = chat?.members
                ?.firstOrNull { it.displayName?.lowercase()?.contains(toName.lowercase()) == true }
                ?.email

            if (emailFromCache != null) {
                callback.onSpeechReady("Ich starte den Anruf mit $toName.")
                TeamsCallService.startCall(context, emailFromCache, toName)
            } else {
                Log.w(TAG, "TEAMS_CALL: keine E-Mail für '$toName' ermittelbar. params=${intent.params}")
                callback.onError("Ich konnte die E-Mail-Adresse von $toName nicht ermitteln. Bitte nenne die E-Mail-Adresse direkt.")
            }
        }
    }


    /**
     * GET_CALENDAR: Termine für heute / morgen / nächster Termin abrufen.
     *
     * Erwartet params: date ("today" | "tomorrow" | "next")
     */
    private fun handleGetCalendar(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        ioScope.launch {
            val date = intent.params["date"]?.lowercase() ?: "today"
            val events = when (date) {
                "tomorrow" -> calendarService.getTomorrowEvents()
                "next"     -> {
                    val next = calendarService.getNextEvent()
                    if (next != null) listOf(next) else emptyList()
                }
                else       -> calendarService.getTodayEvents()
            }
            callback.onSpeechReady(calendarService.buildSpeechText(events))
        }
    }

    /**
     * CREATE_EVENT: Neuen Termin anlegen.
     *
     * Erwartet params: title, date (ISO-Datum, z.B. "2024-01-15"),
     *                  time (Uhrzeit, z.B. "14:00"), location (optional),
     *                  attendee (E-Mail-Adresse, optional)
     */
    private fun handleCreateEvent(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val title = intent.params["title"]
        val date  = intent.params["date"]
        val time  = intent.params["time"]

        if (title == null || date == null || time == null) {
            Log.w(TAG, "CREATE_EVENT: fehlende Parameter. params=${intent.params}")
            callback.onError("Ich konnte den Termin nicht erstellen. Titel, Datum oder Uhrzeit fehlen.")
            return
        }

        ioScope.launch {
            // Startzeit: date + time, Endzeit: +1 Stunde
            val startDateTime = "${date}T${time}:00"
            val endDateTime   = buildEndTime(date, time)

            val location  = intent.params["location"]
            val attendee  = intent.params["attendee"]

            val request = CreateEventRequest(
                subject  = title,
                start    = DateTimeTimeZone(startDateTime, "Europe/Berlin"),
                end      = DateTimeTimeZone(endDateTime, "Europe/Berlin"),
                location = location?.let { Location(it) },
                attendees = attendee?.let {
                    listOf(Attendee(EmailAddressCalendar(name = null, address = it)))
                }
            )

            val success = calendarService.createEvent(request)
            if (success) {
                val locationSuffix = if (!location.isNullOrBlank()) " bei $location" else ""
                callback.onSpeechReady("Termin \"$title\" am $date um $time Uhr$locationSuffix wurde erstellt.")
            } else {
                callback.onError("Der Termin konnte leider nicht erstellt werden. Bitte prüfe deine Internetverbindung.")
            }
        }
    }

    /**
     * NAVIGATE: Navigation zu einer Adresse oder einem Kontaktnamen starten.
     *
     * Erwartet params: address (Zieladresse) oder contact_name (Suchbegriff)
     */
    private fun handleNavigate(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val address = intent.params["address"]
            ?: intent.params["contact_name"]

        if (address == null) {
            Log.w(TAG, "NAVIGATE: fehlender address/contact_name. params=${intent.params}")
            callback.onError("Ich konnte die Navigation nicht starten. Die Zieladresse fehlt.")
            return
        }

        NavigationService.navigateTo(context, address)
        callback.onSpeechReady("Ich starte die Navigation zu $address.")
    }

    /**
     * PLAY_MUSIC: Musik über Spotify abspielen.
     *
     * Erwartet params:
     *  - query  – Suchbegriff (Pflicht), z.B. "AC/DC", "Lofi Beats", "Beethoven"
     *  - type   – Typ (optional): "artist" | "playlist" | "album"
     *
     * Steuerbefehle (query = "pause", "weiter", "nächstes", "zurück"):
     *  Statt einer Suche wird der entsprechende Playback-Befehl gesendet.
     */
    private fun handlePlayMusic(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val query = intent.params["query"]
        val type  = intent.params["type"]

        if (query == null) {
            Log.w(TAG, "PLAY_MUSIC: fehlender query. params=${intent.params}")
            callback.onError("Ich konnte Spotify nicht starten. Was soll ich abspielen?")
            return
        }

        // Steuerungsbefehle abfangen (kein Suchaufruf nötig)
        when (query.lowercase().trim()) {
            "pause", "stop", "stopp" -> {
                spotifyService.togglePlayPause(context)
                callback.onSpeechReady("Musik pausiert.")
                return
            }
            "weiter", "play", "fortsetzen", "resume" -> {
                spotifyService.togglePlayPause(context)
                callback.onSpeechReady("Musik wird fortgesetzt.")
                return
            }
            "nächstes", "next", "skip", "überspringen" -> {
                spotifyService.skipToNext(context)
                callback.onSpeechReady("Nächster Song.")
                return
            }
            "zurück", "previous", "vorheriges" -> {
                spotifyService.skipToPrevious(context)
                callback.onSpeechReady("Vorheriger Song.")
                return
            }
        }

        // Musik suchen und abspielen
        val started = spotifyService.playSearch(context, query, type)
        if (started) {
            val typeSuffix = when (type?.lowercase()) {
                "artist"   -> "den Künstler $query"
                "playlist" -> "die Playlist $query"
                "album"    -> "das Album $query"
                else       -> query
            }
            callback.onSpeechReady("Ich spiele $typeSuffix auf Spotify.")
        } else {
            callback.onSpeechReady("Spotify ist nicht installiert. Ich öffne den Play Store.")
        }
    }

    /**
     * Berechnet die Endzeit als ISO-8601-String (+1 Stunde ab Startzeit).
     * Einfache String-Manipulation, keine Zeitzonen-Konvertierung nötig.
     */
    private fun buildEndTime(date: String, time: String): String {
        return try {
            val parts  = time.split(":")
            val hour   = parts[0].toInt()
            val minute = parts.getOrNull(1)?.toInt() ?: 0
            val endHour = (hour + 1) % 24
            "${date}T%02d:%02d:00".format(endHour, minute)
        } catch (_: Exception) {
            "${date}T${time}:00"
        }
    }

    // ── Phase-1-Handler ──────────────────────────────────────────────────────

    /**
     * READ_EMAILS: Ungelesene E-Mails via Graph API abrufen und vorlesen.
     *
     * - 0 Mails  → "Du hast keine ungelesenen E-Mails."
     * - ≥1 Mails → erste Mail vorlesen, dann Rückfrage
     * - Jede vorgelesene Mail wird danach als gelesen markiert.
     */
    private fun handleReadEmails(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        ioScope.launch {
            val emails = mailService.getUnreadEmails()

            if (emails.isEmpty()) {
                callback.onSpeechReady("Du hast keine ungelesenen E-Mails.")
                return@launch
            }

            val first = emails.first()
            val sender = first.from?.emailAddress?.name
                ?: first.from?.emailAddress?.address
                ?: "Unbekannt"
            val subject = first.subject ?: "Kein Betreff"
            val preview = first.bodyPreview?.take(200) ?: ""

            val speechText = buildString {
                append("Du hast ${emails.size} ungelesene ")
                append(if (emails.size == 1) "E-Mail." else "E-Mails.")
                append(" Erste Mail von $sender: $subject.")
                if (preview.isNotBlank()) append(" $preview")
                append(" Möchtest du weitere Mails hören oder eine beantworten?")
            }

            // Als gelesen markieren
            mailService.markEmailAsRead(first.id)

            callback.onSpeechReady(speechText)
        }
    }

    /**
     * REPLY_EMAIL: E-Mail beantworten.
     *
     * Erwartet params: email_id (optional – Fallback auf letzte Mail), reply_text.
     */
    private fun handleReplyEmail(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val replyText = intent.params["reply_text"]

        if (replyText == null) {
            Log.w(TAG, "REPLY_EMAIL: fehlender reply_text. params=${intent.params}")
            callback.onError("Ich konnte die E-Mail nicht beantworten. Der Antworttext fehlt.")
            return
        }

        // email_id aus params oder letzte abgerufene Mail aus SessionContext
        val emailId = intent.params["email_id"]
            ?: mailService.getLastFetchedEmails().firstOrNull()?.id

        if (emailId == null) {
            Log.w(TAG, "REPLY_EMAIL: keine email_id und kein SessionContext-Eintrag.")
            callback.onError("Ich weiß nicht, auf welche Mail ich antworten soll. Bitte lies zuerst deine Mails vor.")
            return
        }

        ioScope.launch {
            val success = mailService.replyToEmail(emailId, replyText)
            if (success) {
                callback.onSpeechReady("Deine Antwort wurde gesendet.")
            } else {
                callback.onError("Die Antwort konnte leider nicht gesendet werden. Bitte prüfe deine Internetverbindung.")
            }
        }
    }

    /**
     * COMPOSE_EMAIL: Neue E-Mail senden.
     *
     * Erwartet params: to_name, to_address (optional), subject, body.
     * Falls to_address fehlt, wird to_name als E-Mail-Adresse interpretiert.
     */
    private fun handleComposeEmail(
        intent: AssistantIntent,
        callback: IntentResultCallback,
    ) {
        val toName  = intent.params["to_name"]
        val subject = intent.params["subject"]
        val body    = intent.params["body"]

        if (toName == null || subject == null || body == null) {
            Log.w(TAG, "COMPOSE_EMAIL: fehlende Parameter. params=${intent.params}")
            callback.onError("Ich konnte die E-Mail nicht senden. Empfänger, Betreff oder Text fehlen.")
            return
        }

        // to_address ist optional: falls nicht vorhanden, to_name als Adresse nutzen
        val toAddress = intent.params["to_address"] ?: toName

        ioScope.launch {
            val success = mailService.sendNewEmail(
                toName    = toName,
                toAddress = toAddress,
                subject   = subject,
                body      = body,
            )
            if (success) {
                callback.onSpeechReady("Deine E-Mail an $toName wurde gesendet.")
            } else {
                callback.onError("Die E-Mail konnte leider nicht gesendet werden. Bitte prüfe deine Internetverbindung.")
            }
        }
    }
}
