package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.api.GraphTeamsApi
import com.driveassistant.data.model.teams.MessageContent
import com.driveassistant.data.model.teams.SendMessageRequest
import com.driveassistant.data.model.teams.TeamsChat
import com.driveassistant.data.model.teams.TeamsMessage
import kotlinx.coroutines.delay

/**
 * GraphTeamsService
 * ═════════════════
 * Business-Logik für Microsoft Teams-Chats und -Nachrichten via Graph API.
 *
 * Features:
 * - Chat-Liste abrufen und in [chatCache] merken (kein Doppel-Call)
 * - Chat mit Person nach Name suchen (case-insensitive)
 * - Letzte Nachrichten eines Chats abrufen
 * - Nachricht in einen Chat senden
 * - Vorlesbaren Text aus Nachrichtenliste generieren
 * - Polling auf neue Nachrichten (für späteren WorkManager-Sub-Task)
 *
 * Benötigte Scopes: `Chat.ReadWrite`
 * Sub-Task 2.2 – MS Teams Integration
 */
class GraphTeamsService(context: Context) {

    companion object {
        private const val TAG = "GraphTeamsService"
        private const val POLL_INTERVAL_MS = 60_000L
    }

    private val api: GraphTeamsApi = GraphApiClient.retrofit.create(GraphTeamsApi::class.java)

    /** Chat-Cache: wird bei [getChats] befüllt, bei [findChatWithPerson] wiederverwendet. */
    private var chatCache: List<TeamsChat> = emptyList()

    /** Letzte aktiv verwendete Chat-ID – für Polling-Zwecke. */
    private var lastActiveChatId: String? = null

    // ── Öffentliche Methoden ──────────────────────────────────────────────────

    /**
     * Ruft alle Chats des angemeldeten Nutzers ab und befüllt den [chatCache].
     *
     * @return Liste aller Chats (leer bei Fehler)
     */
    suspend fun getChats(): List<TeamsChat> {
        return try {
            val response = api.getChats()
            chatCache = response.value
            Log.d(TAG, "Chats geladen: ${chatCache.size}")
            chatCache
        } catch (e: Exception) {
            Log.e(TAG, "getChats fehlgeschlagen", e)
            emptyList()
        }
    }

    /**
     * Sucht im [chatCache] nach einem oneOnOne-Chat mit der genannten Person.
     * Falls der Cache leer ist, wird zuerst [getChats] aufgerufen.
     *
     * @param name Anzeigename (case-insensitive Teilstring-Suche)
     * @return Gefundener [TeamsChat] oder null
     */
    suspend fun findChatWithPerson(name: String): TeamsChat? {
        if (chatCache.isEmpty()) {
            getChats()
        }
        val nameLower = name.lowercase()
        return chatCache.firstOrNull { chat ->
            chat.members?.any { member ->
                member.displayName?.lowercase()?.contains(nameLower) == true
            } == true
        }
    }

    /**
     * Ruft die letzten 10 Nachrichten eines Chats ab (neueste zuerst).
     *
     * @param chatId Graph-Chat-ID
     * @return Liste der Nachrichten (leer bei Fehler)
     */
    suspend fun getRecentMessages(chatId: String): List<TeamsMessage> {
        return try {
            lastActiveChatId = chatId
            val response = api.getChatMessages(chatId)
            Log.d(TAG, "Nachrichten geladen für Chat $chatId: ${response.value.size}")
            response.value
        } catch (e: Exception) {
            Log.e(TAG, "getRecentMessages fehlgeschlagen für Chat $chatId", e)
            emptyList()
        }
    }

    /**
     * Sendet eine Textnachricht in einen Chat.
     *
     * @param chatId Graph-Chat-ID
     * @param text   Nachrichtentext (Klartext)
     * @return true bei Erfolg, false bei Fehler
     */
    suspend fun sendMessage(chatId: String, text: String): Boolean {
        return try {
            val request = SendMessageRequest(
                body = MessageContent(content = text, contentType = "text")
            )
            api.sendMessage(chatId, request)
            Log.d(TAG, "Nachricht gesendet an Chat $chatId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage fehlgeschlagen für Chat $chatId", e)
            false
        }
    }

    /**
     * Baut einen vorlesbaren Text aus einer Nachrichtenliste.
     *
     * Beispiel:
     * "3 neue Nachrichten von Max Mustermann.
     *  Erste Nachricht: Ich komme 10 Minuten später.
     *  Zweite Nachricht: ..."
     *
     * @param messages        Liste der Nachrichten (neueste zuerst)
     * @param chatPartnerName Anzeigename des Chat-Partners
     * @return Vorlesbarer Text
     */
    fun buildMessageSpeechText(messages: List<TeamsMessage>, chatPartnerName: String): String {
        if (messages.isEmpty()) {
            return "Es gibt keine neuen Nachrichten von $chatPartnerName."
        }

        // Reihenfolge umkehren: älteste zuerst vorlesen
        val ordered = messages.reversed()

        return buildString {
            append("${messages.size} ")
            append(if (messages.size == 1) "Nachricht" else "Nachrichten")
            append(" von $chatPartnerName. ")

            ordered.forEachIndexed { index, message ->
                val sender = message.from?.user?.displayName ?: chatPartnerName
                val text = message.body.content
                    .replace(Regex("<[^>]+>"), "") // HTML-Tags entfernen
                    .trim()
                    .take(300)

                when (index) {
                    0    -> append("Erste Nachricht von $sender: $text. ")
                    1    -> append("Zweite Nachricht: $text. ")
                    2    -> append("Dritte Nachricht: $text. ")
                    else -> append("Weitere Nachricht: $text. ")
                }
            }
        }.trim()
    }

    /**
     * Prüft alle 60 Sekunden auf neue Nachrichten im zuletzt aktiven Chat
     * und ruft [callback] für jede neue Nachricht auf.
     *
     * Hinweis: Diese Methode läuft bis zum Abbruch des Coroutine-Scopes.
     * In einem späteren Sub-Task wird sie durch WorkManager ersetzt.
     *
     * @param callback Wird für jede neue Nachricht aufgerufen
     */
    suspend fun pollNewMessages(callback: (TeamsMessage) -> Unit) {
        var lastSeenId: String? = null

        while (true) {
            delay(POLL_INTERVAL_MS)

            val chatId = lastActiveChatId ?: continue

            try {
                val messages = api.getChatMessages(chatId, top = 5).value
                val newest = messages.firstOrNull() ?: continue

                if (lastSeenId == null) {
                    // Initialer Lauf: nur ID merken, nicht melden
                    lastSeenId = newest.id
                } else if (newest.id != lastSeenId) {
                    // Neue Nachrichten seit letztem Poll
                    val newMessages = messages.takeWhile { it.id != lastSeenId }
                    newMessages.reversed().forEach { callback(it) }
                    lastSeenId = newest.id
                }
            } catch (e: Exception) {
                Log.e(TAG, "pollNewMessages fehlgeschlagen für Chat $chatId", e)
            }
        }
    }
}
