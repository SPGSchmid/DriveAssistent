package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.data.local.AppDatabase
import com.driveassistant.data.local.PlaudImportRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PlaudImportService
 * ══════════════════
 * Haupt-Logik für den PLAUD-Transkript-Import.
 *
 * Ablauf pro Datei:
 *  1. Dateipfad in Room als PENDING eintragen (Duplikat-Schutz via IGNORE)
 *  2. Prüfen ob Datei bereits erfolgreich importiert wurde → überspringen
 *  3. Datei-Inhalt lesen
 *  4. [PlaudAnalysisService.analyzeTranscript] aufrufen (Groq-KI-Analyse)
 *  5. [HubSpotService.searchContact] aufrufen (Kontakt-Matching)
 *  6. [HubSpotService.createNote] aufrufen (Notiz anlegen)
 *  7. Room-Status auf SUCCESS oder FAILED aktualisieren
 *
 * Sub-Task 3.2 – PLAUD-Transkript-Import in HubSpot
 */
class PlaudImportService(private val context: Context) {

    companion object {
        private const val TAG = "PlaudImportService"

        /** Typische PLAUD-Export-Verzeichnisse auf Android-Geräten. */
        val PLAUD_SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/PLAUD",
            "/storage/emulated/0/PLAUD",
            "/storage/emulated/0/Download"
        )

        /** Unterstützte Dateiendungen für PLAUD-Transkripte. */
        val SUPPORTED_EXTENSIONS = listOf(".txt", ".md", ".json")

        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_FAILED  = "FAILED"
    }

    private val dao             by lazy { AppDatabase.getInstance(context).plaudImportDao() }
    private val analysisService by lazy { PlaudAnalysisService(context) }
    private val hubSpotService  by lazy { HubSpotService(context) }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Scannt alle bekannten PLAUD-Verzeichnisse nach neuen (noch nicht importierten)
     * Transkript-Dateien und importiert sie in HubSpot.
     *
     * @return Anzahl erfolgreich importierter Transkripte
     */
    suspend fun scanAndImport(): Int {
        var successCount = 0

        for (dirPath in PLAUD_SEARCH_DIRS) {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) continue

            val candidates = dir.listFiles { file ->
                file.isFile && SUPPORTED_EXTENSIONS.any { ext ->
                    file.name.endsWith(ext, ignoreCase = true)
                }
            } ?: continue

            Log.d(TAG, "Verzeichnis $dirPath: ${candidates.size} Kandidaten gefunden")

            for (file in candidates) {
                // Bereits erfolgreich importiert → überspringen
                val existing = dao.findByPath(file.absolutePath)
                if (existing?.status == STATUS_SUCCESS) {
                    Log.d(TAG, "Bereits importiert: ${file.name} – übersprungen")
                    continue
                }

                val record = importFile(file)
                if (record.status == STATUS_SUCCESS) successCount++
            }
        }

        Log.i(TAG, "Scan abgeschlossen: $successCount neue Transkripte importiert")
        return successCount
    }

    /**
     * Importiert eine einzelne Transkript-Datei vollständig:
     * Analyse → Kontakt-Suche → HubSpot-Notiz → Room-Update.
     *
     * Fehler werden abgefangen und als FAILED-Record zurückgegeben
     * (kein Exception-Propagation – der Worker soll immer stabil bleiben).
     *
     * @return [PlaudImportRecord] mit finalem Status SUCCESS oder FAILED
     */
    suspend fun importFile(file: File): PlaudImportRecord {
        val now = System.currentTimeMillis()

        // Datei als PENDING in Room eintragen (IGNORE falls schon vorhanden)
        dao.insert(
            PlaudImportRecord(
                filePath    = file.absolutePath,
                fileName    = file.name,
                importedAt  = now,
                hubSpotNoteId = null,
                contactName = null,
                status      = STATUS_PENDING
            )
        )

        return try {
            // 1. Datei-Inhalt lesen
            val transcriptText = file.readText(Charsets.UTF_8)
            if (transcriptText.isBlank()) {
                Log.w(TAG, "Leere Datei: ${file.name} – übersprungen")
                dao.updateStatus(file.absolutePath, STATUS_FAILED, null)
                return buildRecord(file, now, STATUS_FAILED, null, null)
            }

            // 2. KI-Analyse via Groq
            val analysis = analysisService.analyzeTranscript(transcriptText)
            Log.d(TAG, "Analyse für ${file.name}: Kontakt=${analysis.contactName}, Sentiment=${analysis.sentiment}")

            // 3. Kontakt in HubSpot suchen (optional – Notiz wird auch ohne Kontakt angelegt)
            val contact = if (!analysis.contactName.isNullOrBlank()) {
                hubSpotService.searchContact(analysis.contactName)
            } else {
                null
            }

            // 4. Notiz-Text formatieren und in HubSpot anlegen
            val noteText = formatNoteText(analysis, file.name)
            val noteId: String? = if (contact != null) {
                val success = hubSpotService.createNote(contact.id, noteText)
                if (success) contact.id else null  // noteId = contactId als Referenz
            } else {
                Log.w(TAG, "Kein Kontakt für '${analysis.contactName}' gefunden – Notiz ohne Kontakt-Verknüpfung")
                null
            }

            val status = if (contact != null && noteId != null) STATUS_SUCCESS else STATUS_FAILED

            // 5. Room-Status aktualisieren
            dao.updateStatus(file.absolutePath, status, noteId)

            buildRecord(file, now, status, noteId, analysis.contactName)

        } catch (e: Exception) {
            Log.e(TAG, "Import fehlgeschlagen für ${file.name}", e)
            dao.updateStatus(file.absolutePath, STATUS_FAILED, null)
            buildRecord(file, now, STATUS_FAILED, null, null)
        }
    }

    // ── Interne Hilfsmethoden ────────────────────────────────────────────────

    /**
     * Formatiert die KI-Analyse als lesbaren HubSpot-Notiz-Text.
     *
     * Beispiel:
     * ```
     * 📝 PLAUD-Transkript vom 03.11.2026
     *
     * Zusammenfassung:
     * Der Kunde zeigte Interesse an Angebot X und bat um ein Folge-Meeting.
     *
     * Action Items:
     * • Angebot bis Freitag nachreichen
     * • Folgetermin für nächste Woche vereinbaren
     * ```
     */
    private fun formatNoteText(
        analysis: PlaudAnalysisService.TranscriptAnalysis,
        fileName: String,
    ): String {
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date())
        return buildString {
            appendLine("📝 PLAUD-Transkript vom $dateStr")
            appendLine()
            appendLine("Zusammenfassung:")
            appendLine(analysis.summary)
            if (analysis.actionItems.isNotEmpty()) {
                appendLine()
                appendLine("Action Items:")
                analysis.actionItems.forEach { item ->
                    appendLine("• $item")
                }
            }
            if (analysis.sentiment != "neutral") {
                appendLine()
                append("Stimmung: ${analysis.sentiment}")
            }
        }.trimEnd()
    }

    /** Erzeugt einen [PlaudImportRecord] aus den vorliegenden Einzelwerten. */
    private fun buildRecord(
        file: File,
        importedAt: Long,
        status: String,
        noteId: String?,
        contactName: String?,
    ) = PlaudImportRecord(
        filePath    = file.absolutePath,
        fileName    = file.name,
        importedAt  = importedAt,
        hubSpotNoteId = noteId,
        contactName = contactName,
        status      = status
    )
}
