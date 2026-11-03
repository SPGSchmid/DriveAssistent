package com.driveassistant.service

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * MainCarAppService
 * ═════════════════
 * Zentraler Einstiegspunkt für Android Auto.
 *
 * Der Android Auto Host (z. B. das Head-Unit-System im Auto oder der
 * Desktop Head Unit Simulator) bindet diesen Service beim Start der App.
 *
 * Registrierung in AndroidManifest.xml:
 *   - permission="android.car.permission.BIND_CAR_APP_SERVICE"
 *   - intent-filter action: "androidx.car.app.CarAppService"
 *   - category: "androidx.car.app.category.POI"
 *
 * Lifecycle:
 *   CarAppService → onCreateSession() → DriveAssistantSession → HomeScreen
 *
 * Dokumentation:
 *   https://developer.android.com/training/cars/apps#app-service
 */
class MainCarAppService : CarAppService() {

    /**
     * Wird vom Android Auto Host aufgerufen, wenn eine neue Sitzung gestartet
     * wird (z. B. beim Verbinden des Smartphones mit dem Auto).
     *
     * Gibt eine neue [DriveAssistantSession]-Instanz zurück.
     */
    override fun onCreateSession(): Session = DriveAssistantSession()

    /**
     * Definiert, welchen Hosts vertraut wird.
     *
     * ALLOW_ALL_HOSTS: akzeptiert alle Hosts – geeignet für Entwicklung und
     * Sideload-Distribution. Für einen Play-Store-Release sollte
     * [HostValidator.createDefaultHostValidator] verwendet werden, der
     * nur Google-zertifizierte Hosts akzeptiert.
     *
     * Da diese App per APK Sideload verteilt wird (laut Plan), ist
     * ALLOW_ALL_HOSTS die richtige Wahl.
     */
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
}
