package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.api.GraphMailApi
import com.driveassistant.data.local.AppDatabase
import com.driveassistant.data.local.CachedEmail
import com.driveassistant.data.model.email.EmailAddress
import com.driveassistant.data.model.email.EmailMessage
import com.driveassistant.data.model.email.MessageBody
import com.driveassistant.data.model.email.NewMessage
import com.driveassistant.data.model.email.Recipient
import com.driveassistant.data.model.email.ReplyMailRequest
import com.driveassistant.data.model.email.SendMailRequest
import java.util.concurrent.TimeUnit

/**
 * GraphMailService
 * ════════════════
 * Business-Logik für alle E-Mail-Operationen via Microsoft Graph API.
 *
 * Features:
 *  - Ungelesene Mails abrufen mit Offline-Fallback via Room-Cache
 *  - Letzte vorgelesene Mail wird im [SessionContext] gespeichert
 *    (für Rückbezüge wie "beantworte diese Mail")
 *  - E-Mail beantworten, neue Mail senden, als gelesen markieren
 *
 * Verwendung:
 * ```kotlin
 * val service = GraphMailService(context)
 * val mails   = service.getUnreadEmails()   // online oder aus Cache
 * ```
 */
class GraphMailService(context: Context) {

    companion object {
        private const val TAG = "GraphMailService"

        /** Cache-TTL: Einträge älter als 24 Stunden werden beim nächsten Abruf entfernt. */
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)
    }

    private val api: GraphMailApi = GraphApiClient.retrofit.create(GraphMailApi::class.java)
    private val emailDao = AppDatabase.getInstance(context).emailDao()

    // Letzte abgerufene Mails – für Rückbezüge im selben Session-Kontext
    private var lastFetchedEmails: List<EmailMessage> = emptyList()

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Ruft ungelesene E-Mails ab.
     *
     * Online: Graph API wird abgefragt, Ergebnis im Room-Cache gespeichert.
     * Offline: Gecachte Einträge aus Room werden zurückgegeben.
     *
     * Die abgerufene Liste wird intern für Rückbezüge zwischengespeichert
     * (z. B. "beantworte die letzte Mail").
     *
     * @return Liste ungelesener [EmailMessage], ggf. leer wenn keine Mails vorhanden.
     */
    suspend fun getUnreadEmails(): List<EmailMessage> {
        return try {
            val response = api.getUnreadMessages()
            val emails = response.value
            Log.d(TAG, "${emails.size} ungelesene Mails abgerufen.")

            // Cache aktualisieren
            cacheEmails(emails)

            lastFetchedEmails = emails
            emails
        } catch (e: Exception) {
            Log.w(TAG, "Graph API nicht erreichbar – lade aus Cache.", e)
            loadFromCache()
        }
    }

    /**
     * Antwortet auf eine E-Mail.
     *
     * @param emailId   Graph-API-ID der E-Mail (aus [EmailMessage.id]).
     * @param replyText Text der Antwort.
     * @return `true` wenn die Antwort erfolgreich gesendet wurde.
     */
    suspend fun replyToEmail(emailId: String, replyText: String): Boolean {
        return try {
            val response = api.replyToMessage(emailId, ReplyMailRequest(comment = replyText))
            val success = response.isSuccessful
            if (success) {
                Log.d(TAG, "Antwort auf Mail $emailId gesendet.")
                markEmailAsRead(emailId)
            } else {
                Log.w(TAG, "replyToEmail fehlgeschlagen: HTTP ${response.code()}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "replyToEmail Exception", e)
            false
        }
    }

    /**
     * Sendet eine neue E-Mail.
     *
     * @param toName     Anzeigename des Empfängers.
     * @param toAddress  E-Mail-Adresse des Empfängers.
     * @param subject    Betreff.
     * @param body       Nachrichtentext (Plaintext).
     * @return `true` wenn die Mail erfolgreich gesendet wurde.
     */
    suspend fun sendNewEmail(
        toName: String,
        toAddress: String,
        subject: String,
        body: String,
    ): Boolean {
        return try {
            val request = SendMailRequest(
                message = NewMessage(
                    subject = subject,
                    body = MessageBody(content = body),
                    toRecipients = listOf(
                        Recipient(EmailAddress(name = toName, address = toAddress))
                    ),
                ),
            )
            val response = api.sendMail(request)
            val success = response.isSuccessful
            if (success) {
                Log.d(TAG, "Neue Mail an $toAddress gesendet.")
            } else {
                Log.w(TAG, "sendNewEmail fehlgeschlagen: HTTP ${response.code()}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "sendNewEmail Exception", e)
            false
        }
    }

    /**
     * Markiert eine E-Mail als gelesen.
     * Stille Operation – kein TTS-Feedback nötig.
     *
     * @param emailId Graph-API-ID der E-Mail.
     */
    suspend fun markEmailAsRead(emailId: String) {
        try {
            api.markAsRead(emailId, mapOf("isRead" to true))
            Log.d(TAG, "Mail $emailId als gelesen markiert.")
        } catch (e: Exception) {
            Log.w(TAG, "markEmailAsRead fehlgeschlagen", e)
        }
    }

    /**
     * Gibt die zuletzt abgerufene Mail zurück – für Rückbezüge
     * wie "beantworte diese Mail" ohne erneuten API-Call.
     */
    fun getLastFetchedEmails(): List<EmailMessage> = lastFetchedEmails

    // ── Cache-Logik ──────────────────────────────────────────────────────────

    /**
     * Speichert die abgerufenen Mails im Room-Cache und entfernt veraltete Einträge.
     */
    private suspend fun cacheEmails(emails: List<EmailMessage>) {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        emailDao.deleteOlderThan(cutoff)

        val cachedEmails = emails.map { it.toCachedEmail() }
        emailDao.insertAll(cachedEmails)
        Log.d(TAG, "${cachedEmails.size} Mails gecacht.")
    }

    /**
     * Lädt ungelesene Mails aus dem Room-Cache und konvertiert sie zurück
     * in [EmailMessage]-Objekte für einheitliche Verarbeitung im [IntentRouter].
     */
    private suspend fun loadFromCache(): List<EmailMessage> {
        val cached = emailDao.getUnreadEmails()
        Log.d(TAG, "${cached.size} Mails aus Cache geladen.")
        val emails = cached.map { it.toEmailMessage() }
        lastFetchedEmails = emails
        return emails
    }

    // ── Konvertierungs-Helpers ────────────────────────────────────────────────

    private fun EmailMessage.toCachedEmail() = CachedEmail(
        id                = id,
        subject           = subject,
        bodyPreview       = bodyPreview,
        fromName          = from?.emailAddress?.name,
        fromAddress       = from?.emailAddress?.address ?: "",
        isRead            = isRead,
        receivedDateTime  = receivedDateTime,
        importance        = importance,
    )

    private fun CachedEmail.toEmailMessage() = EmailMessage(
        id               = id,
        subject          = subject,
        bodyPreview      = bodyPreview,
        from             = Recipient(EmailAddress(name = fromName, address = fromAddress)),
        isRead           = isRead,
        receivedDateTime = receivedDateTime,
        importance       = importance,
    )
}
