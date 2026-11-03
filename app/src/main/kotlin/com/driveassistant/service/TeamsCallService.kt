package com.driveassistant.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TeamsCallService
 * ════════════════
 * Startet einen Microsoft Teams-Anruf via Deep Link.
 *
 * Deep-Link-Schema: `msteams://call?users=EMAIL`
 * Falls Microsoft Teams nicht installiert ist, wird der Play Store geöffnet.
 *
 * Getroffene Entscheidung laut Plan: Teams-Anrufe über Deep Link (`msteams://`),
 * keine eigene Calling-Implementierung.
 *
 * Sub-Task 2.2 – MS Teams Integration
 */
object TeamsCallService {

    private const val TAG = "TeamsCallService"
    private const val TEAMS_PACKAGE = "com.microsoft.teams"

    /**
     * Startet einen Teams-Anruf via Deep Link.
     *
     * @param context     Android-Context (muss nicht Activity sein)
     * @param email       E-Mail-Adresse des Anrufziels
     * @param displayName Anzeigename für Logging und TTS
     */
    fun startCall(context: Context, email: String, displayName: String) {
        Log.d(TAG, "Starte Teams-Anruf mit $displayName ($email)")

        val uri = Uri.parse("msteams://call?users=$email")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.w(TAG, "Teams nicht installiert – öffne Play Store")
            val storeIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$TEAMS_PACKAGE")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(storeIntent)
        }
    }
}
