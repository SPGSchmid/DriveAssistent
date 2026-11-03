package com.driveassistant.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * NavigationService
 * ═════════════════
 * Startet Google Maps Navigation per Intent.
 *
 * Kein Maps SDK erforderlich – die Übergabe erfolgt rein über Android-Intents.
 *
 * Fallback-Verhalten:
 *  1. Versucht Google Maps (`com.google.android.apps.maps`) direkt zu öffnen
 *  2. Falls Maps nicht installiert → `geo:`-Intent als Fallback (öffnet beliebige
 *     Karten-App, die geo-URIs unterstützt)
 *
 * Dokumentation: https://developers.google.com/maps/documentation/urls/android-intents
 *
 * Sub-Task 2.1 / 4.2 – Google Maps Navigation (Intent-basiert)
 */
object NavigationService {

    private const val TAG = "NavigationService"

    /**
     * Startet die Navigation zur angegebenen Adresse oder zu einem Suchbegriff.
     *
     * @param context Android-Context (Application-Context wird bevorzugt)
     * @param address Zieladresse oder Suchbegriff,
     *                z.B. "Musterstraße 1, 80331 München" oder "Firma Müller GmbH"
     */
    fun navigateTo(context: Context, address: String) {
        Log.d(TAG, "Navigation gestartet zu: $address")

        val encoded = Uri.encode(address)
        val mapsUri = Uri.parse("google.navigation:q=$encoded&mode=d")
        val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (mapsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapsIntent)
        } else {
            // Fallback: geo:-URI für beliebige Karten-App
            Log.w(TAG, "Google Maps nicht installiert – Fallback auf geo: URI.")
            val fallbackUri = Uri.parse("geo:0,0?q=$encoded")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }
}
