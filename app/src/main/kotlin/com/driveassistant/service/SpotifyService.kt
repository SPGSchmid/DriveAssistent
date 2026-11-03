package com.driveassistant.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * SpotifyService
 * ══════════════
 * Steuert Spotify per URI-Intent (kein SDK erforderlich).
 *
 * Unterstützte Aktionen:
 *  - Suche nach Künstler, Playlist oder Album abspielen
 *  - Pause/Resume
 *  - Nächster Track
 *  - Vorheriger Track
 *
 * Funktionsweise:
 *  - Wenn Spotify installiert ist → `spotify:`-URI direkt übergeben
 *  - Wenn Spotify nicht installiert → Play Store öffnen
 *
 * Spotify-URI-Schema Referenz:
 *  spotify:search:<query>           – Suchergebnis abspielen
 *  spotify:artist:<id>              – Künstler
 *  spotify:playlist:<id>            – Playlist
 *  spotify:album:<id>               – Album
 *
 * Sub-Task 4.1 – Spotify-Steuerung (Intent-basiert)
 */
object SpotifyService {

    private const val TAG               = "SpotifyService"
    private const val SPOTIFY_PACKAGE   = "com.spotify.music"
    private const val PLAY_STORE_URI    = "market://details?id=$SPOTIFY_PACKAGE"

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Spielt Musik basierend auf einer Suchanfrage ab.
     *
     * @param context Android-Context
     * @param query   Suchbegriff, z.B. "AC/DC", "Motivation Playlist", "Beethoven"
     * @param type    Typ der Suche: "artist", "playlist", "album" oder leer für allgemeine Suche
     * @return `true` wenn Spotify gestartet werden konnte, `false` wenn nicht installiert
     */
    fun playSearch(context: Context, query: String, type: String? = null): Boolean {
        Log.d(TAG, "playSearch: query='$query', type='$type'")

        if (!isSpotifyInstalled(context)) {
            Log.w(TAG, "Spotify nicht installiert – öffne Play Store.")
            openPlayStore(context)
            return false
        }

        // Spotify-URI zusammenbauen: spotify:search:<encoded_query>
        // Der type-Parameter wird der Suchanfrage vorangestellt für bessere Ergebnisse
        val searchQuery = when (type?.lowercase()) {
            "artist"   -> "artist:$query"
            "playlist" -> "playlist:$query"
            "album"    -> "album:$query"
            else       -> query
        }
        val spotifyUri = Uri.parse("spotify:search:${Uri.encode(searchQuery)}")

        return launchSpotifyIntent(context, spotifyUri)
    }

    /**
     * Pause/Resume via Spotify-Broadcast-Intent.
     * Funktioniert nur wenn Spotify bereits läuft.
     */
    fun togglePlayPause(context: Context) {
        Log.d(TAG, "togglePlayPause")
        val intent = Intent("com.spotify.mobile.android.ui.widget.PLAY_PAUSE").apply {
            setPackage(SPOTIFY_PACKAGE)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Nächsten Track überspringen.
     */
    fun skipToNext(context: Context) {
        Log.d(TAG, "skipToNext")
        val intent = Intent("com.spotify.mobile.android.ui.widget.NEXT").apply {
            setPackage(SPOTIFY_PACKAGE)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Vorherigen Track abspielen.
     */
    fun skipToPrevious(context: Context) {
        Log.d(TAG, "skipToPrevious")
        val intent = Intent("com.spotify.mobile.android.ui.widget.PREVIOUS").apply {
            setPackage(SPOTIFY_PACKAGE)
        }
        context.sendBroadcast(intent)
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private fun isSpotifyInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SPOTIFY_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun launchSpotifyIntent(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(SPOTIFY_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun openPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URI)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
