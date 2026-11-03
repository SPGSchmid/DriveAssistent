package com.driveassistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PlaudImportRecord
 * ═════════════════
 * Room-Entity für den Import-Status von PLAUD-Transkript-Dateien.
 *
 * Jede bekannte Datei wird hier einmalig eingetragen (Primärschlüssel = Dateipfad).
 * Das verhindert Doppel-Imports: [PlaudImportDao.findByPath] prüft vor jedem Import,
 * ob die Datei bereits verarbeitet wurde.
 *
 * Status-Werte:
 *  - "PENDING"  – Import gestartet, noch nicht abgeschlossen
 *  - "SUCCESS"  – Transkript wurde erfolgreich als HubSpot-Notiz angelegt
 *  - "FAILED"   – Analyse oder HubSpot-API-Aufruf fehlgeschlagen
 *
 * Sub-Task 3.2 – PLAUD-Transkript-Import in HubSpot
 */
@Entity(tableName = "plaud_imports")
data class PlaudImportRecord(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val importedAt: Long,
    val hubSpotNoteId: String?,   // null wenn Import fehlgeschlagen oder noch läuft
    val contactName: String?,
    val status: String            // "SUCCESS", "FAILED", "PENDING"
)
