package com.driveassistant.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.driveassistant.DriveAssistantApp
import com.driveassistant.R
import com.driveassistant.service.PlaudImportService
import com.driveassistant.ui.PhoneActivity

/**
 * PlaudScanWorker
 * ═══════════════
 * WorkManager-Background-Job, der alle 15 Minuten PLAUD-Export-Verzeichnisse
 * nach neuen Transkripten scannt und diese automatisch in HubSpot importiert.
 *
 * Wird beim App-Start via [com.driveassistant.DriveAssistantApp] als
 * PeriodicWorkRequest registriert (UniquePeriodicWork "plaud_scan").
 *
 * Bei mind. 1 neu importiertem Transkript wird eine Android-Notification
 * im Kanal [DriveAssistantApp.CHANNEL_PLAUD] angezeigt.
 *
 * Sub-Task 3.2 – PLAUD-Transkript-Import in HubSpot
 */
class PlaudScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PlaudScanWorker"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Scan gestartet")

        return try {
            val importService = PlaudImportService(applicationContext)
            val count = importService.scanAndImport()

            if (count > 0) {
                showImportNotification(count)
            }

            Log.i(TAG, "Scan erfolgreich abgeschlossen: $count neue Transkripte")
            Result.success()
        } catch (e: Exception) {
            // Nicht-fatale Fehler: Result.success() damit WorkManager nicht
            // den Job dauerhaft als FAILED markiert und weitere Runs blockiert.
            Log.e(TAG, "Fehler beim PLAUD-Scan", e)
            Result.success()
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    /**
     * Zeigt eine Benachrichtigung über erfolgreich importierte Transkripte.
     * Tippen auf die Notification öffnet die App.
     */
    private fun showImportNotification(count: Int) {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val tapIntent = Intent(applicationContext, PhoneActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title   = applicationContext.getString(R.string.plaud_notification_title)
        val message = applicationContext.resources.getQuantityString(
            R.plurals.plaud_notification_message,
            count,
            count
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            DriveAssistantApp.CHANNEL_PLAUD
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification gesendet: $count Transkripte")
    }
}
