package com.driveassistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * PlaudImportDao
 * ══════════════
 * Room-DAO für [PlaudImportRecord].
 *
 * Wird von [com.driveassistant.service.PlaudImportService] genutzt, um:
 *  - Neue Dateipfade mit PENDING-Status einzutragen (vor dem Import)
 *  - Zu prüfen ob eine Datei bereits importiert wurde (Duplikat-Schutz)
 *  - Den Status nach erfolgreichem oder fehlgeschlagenem Import zu aktualisieren
 *  - Die letzten 20 Importe für Diagnose/Anzeige abzurufen
 *
 * [OnConflictStrategy.IGNORE] bei insert: Falls die Datei bereits eingetragen ist,
 * wird kein Fehler geworfen – der bestehende Eintrag bleibt unverändert.
 *
 * Sub-Task 3.2 – PLAUD-Transkript-Import in HubSpot
 */
@Dao
interface PlaudImportDao {

    /**
     * Trägt einen neuen [PlaudImportRecord] ein.
     * Bei bereits vorhandenem [PlaudImportRecord.filePath] wird der Eintrag ignoriert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: PlaudImportRecord)

    /**
     * Sucht einen Import-Eintrag anhand des Dateipfads.
     * Gibt `null` zurück wenn die Datei noch nie importiert wurde.
     */
    @Query("SELECT * FROM plaud_imports WHERE filePath = :filePath")
    suspend fun findByPath(filePath: String): PlaudImportRecord?

    /**
     * Gibt die 20 zuletzt verarbeiteten Importe zurück (neueste zuerst).
     */
    @Query("SELECT * FROM plaud_imports ORDER BY importedAt DESC LIMIT 20")
    suspend fun getRecentImports(): List<PlaudImportRecord>

    /**
     * Aktualisiert Status und HubSpot-Notiz-ID eines Eintrags nach Abschluss des Imports.
     */
    @Query("UPDATE plaud_imports SET status = :status, hubSpotNoteId = :noteId WHERE filePath = :filePath")
    suspend fun updateStatus(filePath: String, status: String, noteId: String?)
}
