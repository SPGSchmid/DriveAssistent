package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.data.model.hubspot.AssociationTo
import com.driveassistant.data.model.hubspot.AssociationType
import com.driveassistant.data.model.hubspot.CreateNoteRequest
import com.driveassistant.data.model.hubspot.HubSpotContact
import com.driveassistant.data.model.hubspot.HubSpotDeal
import com.driveassistant.data.model.hubspot.NoteAssociation
import com.driveassistant.data.model.hubspot.NoteProperties
import com.driveassistant.data.model.hubspot.SearchRequest
import java.time.Instant

/**
 * HubSpotService
 * ══════════════
 * Business-Logic für HubSpot CRM-Operationen.
 *
 * Features:
 * - Kontakt-Suche nach Name (Vor- + Nachname)
 * - Kontaktdaten als TTS-Text formatieren
 * - Offene Deals abrufen
 * - Deals als TTS-Text formatieren
 * - Notiz zu einem Kontakt anlegen
 * - Letzten gefundenen Kontakt cachen (für Folgebefehle wie "erstelle eine Notiz zu diesem Kontakt")
 *
 * Sub-Task 3.1 – HubSpot CRM Integration
 */
class HubSpotService(context: Context) {

    companion object {
        private const val TAG = "HubSpotService"
    }

    private val api = HubSpotClient.also { it.init(context) }.api

    /** Letzter erfolgreich gefundener Kontakt – für Rückbezüge in Folgebefehlen. */
    private var lastFoundContact: HubSpotContact? = null

    // ── Kontakt-Suche ─────────────────────────────────────────────────────────

    /**
     * Sucht einen Kontakt in HubSpot nach Vor- und/oder Nachname.
     *
     * Der gefundene Kontakt wird in [lastFoundContact] gecacht.
     * Gibt `null` zurück wenn kein Treffer gefunden wurde oder ein Fehler auftritt.
     */
    suspend fun searchContact(name: String): HubSpotContact? {
        return try {
            val request = SearchRequest(
                query = name,
                limit = 5,
                properties = listOf("firstname", "lastname", "email", "phone", "company", "address", "city", "jobtitle")
            )
            val response = api.searchContacts(request)
            val contact = response.results.firstOrNull()
            if (contact != null) {
                lastFoundContact = contact
                Log.d(TAG, "Kontakt gefunden: id=${contact.id}")
            } else {
                Log.d(TAG, "Kein Kontakt gefunden für '$name'")
            }
            contact
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Kontakt-Suche für '$name'", e)
            null
        }
    }

    /**
     * Formatiert die Kontaktdaten eines [HubSpotContact] für die TTS-Ausgabe.
     *
     * Beispiel: "Kontakt gefunden: Max Müller, Musterfirma GmbH, Vertriebsleiter.
     *            E-Mail: max.mueller@example.com. Telefon: +49 123 456789."
     */
    fun getContactSpeechText(contact: HubSpotContact): String {
        val p = contact.properties
        val name = listOfNotNull(p.firstname, p.lastname).joinToString(" ").ifBlank { "Unbekannt" }

        return buildString {
            append("Kontakt gefunden: $name")
            if (!p.company.isNullOrBlank()) append(", ${p.company}")
            if (!p.jobtitle.isNullOrBlank()) append(", ${p.jobtitle}")
            append(".")
            if (!p.email.isNullOrBlank()) append(" E-Mail: ${p.email}.")
            if (!p.phone.isNullOrBlank()) append(" Telefon: ${p.phone}.")
            if (!p.address.isNullOrBlank() || !p.city.isNullOrBlank()) {
                val addr = listOfNotNull(p.address, p.city).joinToString(", ")
                append(" Adresse: $addr.")
            }
        }
    }

    // ── Deals ─────────────────────────────────────────────────────────────────

    /**
     * Ruft alle offenen Deals aus HubSpot ab.
     *
     * Gibt eine leere Liste zurück bei Fehler (Graceful Degradation).
     */
    suspend fun getDeals(): List<HubSpotDeal> {
        return try {
            api.getDeals().results
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Abrufen der Deals", e)
            emptyList()
        }
    }

    /**
     * Formatiert eine Deal-Liste für die TTS-Ausgabe.
     *
     * Beispiel: "Du hast 2 offene Deals: Angebot Müller über 5000 Euro,
     *            Abschluss am 15. Januar. Projekt ABC über 12000 Euro, kein Abschluss-Datum."
     */
    fun getDealsSpeechText(deals: List<HubSpotDeal>): String {
        if (deals.isEmpty()) return "Du hast aktuell keine offenen Deals."

        return buildString {
            append("Du hast ${deals.size} ${if (deals.size == 1) "offenen Deal" else "offene Deals"}: ")
            deals.forEachIndexed { index, deal ->
                val p = deal.properties
                val name = p.dealname ?: "Unbenannter Deal"
                append(name)
                if (!p.amount.isNullOrBlank()) {
                    // Betrag als ganzzahligen Euro-Wert formatieren (ggf. Kommazahl)
                    val amountFormatted = p.amount.toDoubleOrNull()?.toLong()?.toString() ?: p.amount
                    append(" über $amountFormatted Euro")
                }
                if (!p.closedate.isNullOrBlank()) {
                    // Datum: nur das Datum-Teil (ISO-8601 beginnt mit YYYY-MM-DD)
                    val datePart = p.closedate.take(10)
                    append(", Abschluss am $datePart")
                } else {
                    append(", kein Abschluss-Datum")
                }
                if (index < deals.size - 1) append(". ") else append(".")
            }
        }
    }

    // ── Notizen ───────────────────────────────────────────────────────────────

    /**
     * Legt eine Notiz in HubSpot an und verknüpft sie mit [contactId].
     *
     * @return `true` wenn die Notiz erfolgreich angelegt wurde, sonst `false`.
     */
    suspend fun createNote(contactId: String, noteText: String): Boolean {
        return try {
            val timestamp = Instant.now().toString() // ISO 8601 UTC
            val request = CreateNoteRequest(
                properties = NoteProperties(
                    hs_note_body = noteText,
                    hs_timestamp = timestamp
                ),
                associations = listOf(
                    NoteAssociation(
                        to = AssociationTo(id = contactId),
                        types = listOf(AssociationType())
                    )
                )
            )
            val note = api.createNote(request)
            Log.d(TAG, "Notiz erstellt: id=${note.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen der Notiz für Kontakt '$contactId'", e)
            false
        }
    }

    // ── Kontext-Cache ─────────────────────────────────────────────────────────

    /**
     * Gibt den zuletzt gefundenen Kontakt zurück.
     * Ermöglicht Folgebefehle wie "erstelle eine Notiz zu diesem Kontakt"
     * ohne erneute Namensnennung.
     */
    fun getLastFoundContact(): HubSpotContact? = lastFoundContact
}
