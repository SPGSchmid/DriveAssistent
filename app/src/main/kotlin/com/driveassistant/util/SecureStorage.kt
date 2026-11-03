package com.driveassistant.util

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureStorage
 * ═════════════
 * Wrapper für [EncryptedSharedPreferences] zur sicheren Speicherung
 * sensibler Daten wie API-Keys und OAuth-Tokens.
 *
 * Verwendete Keys (Konventionen):
 *  - "openai_api_key"    – OpenAI API-Key (Sub-Task 1.3)
 *  - "msal_access_token" – Microsoft Graph Access Token (Sub-Task 1.4)
 *  - "hubspot_token"     – HubSpot Private App Token (Sub-Task 3.1)
 *
 * Datei wird AES-256-GCM verschlüsselt und ist nur für diese App lesbar.
 * Niemals Klartext-Keys in Logs oder SharedPreferences speichern.
 *
 * @param context Application-Context
 */
class SecureStorage(context: Context) {

    companion object {
        private const val TAG = "SecureStorage"
        private const val FILE_NAME = "driveassistant_secure_prefs"

        // Bekannte Keys als Konstanten – verhindert Tippfehler an Aufrufstellen
        const val KEY_OPENAI_API_KEY     = "openai_api_key"
        const val KEY_MSAL_ACCESS_TOKEN  = "msal_access_token"
        const val KEY_HUBSPOT_TOKEN      = "hubspot_token"
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Speichert [value] verschlüsselt unter [key].
     * Bestehende Werte werden überschrieben.
     */
    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        Log.d(TAG, "Gespeichert: key='$key'")
    }

    /**
     * Liest den gespeicherten Wert für [key].
     * Gibt `null` zurück wenn kein Wert vorhanden ist.
     */
    fun getString(key: String): String? {
        return prefs.getString(key, null).also { value ->
            if (value == null) {
                Log.d(TAG, "Key nicht gefunden: '$key'")
            }
        }
    }

    /**
     * Entfernt den gespeicherten Wert für [key].
     */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Gelöscht: key='$key'")
    }

    /**
     * Gibt zurück ob ein Wert für [key] gespeichert ist.
     */
    fun contains(key: String): Boolean = prefs.contains(key)
}
