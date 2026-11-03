package com.driveassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.driveassistant.data.local.AppDatabase
import com.driveassistant.service.AuthInitCallback
import com.driveassistant.service.MsalAuthService
import com.driveassistant.worker.PlaudScanWorker
import java.util.concurrent.TimeUnit

/**
 * Application-Klasse des DriveAssistant.
 *
 * Verantwortlichkeiten:
 * - Notification-Channels anlegen (benötigt ab API 26)
 * - Globale Initialisierungen (werden in späteren Sub-Tasks ergänzt)
 */
class DriveAssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeMsal()
        initializeDatabase()
        schedulePlaudScan()
    }

    // ── PLAUD-Scan WorkManager ─────────────────────────────────────────────────

    /**
     * Registriert den [PlaudScanWorker] als periodischen Background-Job.
     *
     * Der Job läuft alle 15 Minuten (WorkManager-Minimum) sobald eine
     * Netzwerkverbindung besteht.
     * [ExistingPeriodicWorkPolicy.KEEP] verhindert, dass bei jedem App-Start
     * ein neuer paralleler Job gestartet wird.
     */
    private fun schedulePlaudScan() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<PlaudScanWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "plaud_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "PLAUD-Scan alle 15 Minuten geplant")
    }

    // ── Datenbank-Initialisierung ──────────────────────────────────────────────

    /**
     * Initialisiert die Room-Datenbank beim App-Start.
     * Der Aufruf stellt sicher, dass die DB-Instanz angelegt ist,
     * bevor der erste E-Mail-Abruf erfolgt.
     */
    private fun initializeDatabase() {
        AppDatabase.getInstance(this)
        Log.d(TAG, "Room-Datenbank bereit")
    }

    // ── MSAL Initialisierung ───────────────────────────────────────────────────

    /**
     * Initialisiert die Microsoft Authentication Library beim App-Start.
     * Muss vor dem ersten Aufruf von [MsalAuthService.signIn] oder
     * [MsalAuthService.getAccessToken] abgeschlossen sein.
     */
    private fun initializeMsal() {
        MsalAuthService.initialize(this, object : AuthInitCallback {
            override fun onInitialized() {
                Log.d(TAG, "MSAL bereit")
            }

            override fun onError(exception: Exception) {
                Log.e(TAG, "MSAL Initialisierung fehlgeschlagen – Graph API nicht verfügbar", exception)
            }
        })
    }

    // ── Notification-Channels ──────────────────────────────────────────────

    /**
     * Legt alle benötigten Notification-Channels an.
     * Ab Android O (API 26) müssen Channels vor dem ersten Senden existieren.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        // Kanal für allgemeine Assistent-Meldungen (E-Mail, Kalender)
        NotificationChannel(
            CHANNEL_GENERAL,
            getString(R.string.notification_channel_general),
            NotificationManager.IMPORTANCE_DEFAULT
        ).also { manager.createNotificationChannel(it) }

        // Kanal für MS Teams-Nachrichten
        NotificationChannel(
            CHANNEL_TEAMS,
            getString(R.string.notification_channel_teams),
            NotificationManager.IMPORTANCE_HIGH
        ).also { manager.createNotificationChannel(it) }

        // Kanal für PLAUD-Import-Bestätigungen
        NotificationChannel(
            CHANNEL_PLAUD,
            getString(R.string.notification_channel_plaud),
            NotificationManager.IMPORTANCE_DEFAULT
        ).also { manager.createNotificationChannel(it) }
    }

    companion object {
        private const val TAG = "DriveAssistantApp"
        const val CHANNEL_GENERAL = "drive_assistant_general"
        const val CHANNEL_TEAMS   = "drive_assistant_teams"
        const val CHANNEL_PLAUD   = "drive_assistant_plaud"
    }
}
