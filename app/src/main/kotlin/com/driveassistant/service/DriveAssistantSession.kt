package com.driveassistant.service

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import com.driveassistant.ui.HomeScreen

/**
 * DriveAssistantSession
 * ══════════════════════
 * Eine [Session] repräsentiert eine einzelne Verbindung zwischen der App
 * und dem Android Auto Host. Sie wird von [MainCarAppService.onCreateSession]
 * erzeugt und lebt so lange, wie der Auto-Host verbunden ist.
 *
 * Verantwortlichkeiten:
 * - Erstellt den initialen Screen ([HomeScreen])
 * - Stellt in späteren Sub-Tasks Lifecycle-Hooks für STT/TTS bereit
 *   (z. B. Mikrofon freigeben wenn Auto trennt)
 *
 * Lifecycle:
 *   onCreateScreen()   → wird aufgerufen sobald Host bereit ist
 *   onNewIntent()      → bei Deep-Links oder internen Navigationsänderungen
 *   onCarConfigurationChanged() → Reaktion auf Dunkel/Hellmodus-Wechsel im Auto
 *
 * Dokumentation:
 *   https://developer.android.com/training/cars/apps#session
 */
class DriveAssistantSession : Session() {

    /**
     * Erstellt und gibt den Start-Screen zurück.
     * Der [HomeScreen] zeigt den Mikrofon-Button und die Statusanzeige.
     *
     * @param intent Startintent (kann im Build 1 ignoriert werden)
     */
    override fun onCreateScreen(intent: android.content.Intent): Screen {
        return HomeScreen(carContext)
    }

    /**
     * Wird aufgerufen, wenn sich die Fahrzeugkonfiguration ändert
     * (z. B. Tag-/Nachtmodus des Displays).
     *
     * Im aktuellen Build wird die Konfigurationsänderung an den Screen
     * weitergegeben; in späteren Sub-Tasks kann hier der Theme-Wechsel
     * explizit behandelt werden.
     */
    override fun onCarConfigurationChanged(newConfiguration: android.content.res.Configuration) {
        super.onCarConfigurationChanged(newConfiguration)
        // Zukünftig: Theme-Neuaufbau oder TTS-Sprache wechseln falls nötig
    }
}
