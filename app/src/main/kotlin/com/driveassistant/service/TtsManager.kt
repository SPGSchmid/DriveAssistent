package com.driveassistant.service

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * TtsManager
 * ══════════
 * Kapselt die Android [TextToSpeech]-Engine.
 *
 * Features:
 *  - Sprache: Deutsch ([Locale.GERMAN])
 *  - Queue-Modus: [TextToSpeech.QUEUE_FLUSH] – laufende Ausgabe wird bei neuer
 *    Eingabe sofort unterbrochen (kein Anstauen)
 *  - Offline-Fallback: falls kein Netz vorhanden ist, gibt die TTS-Engine
 *    den übergebenen Text trotzdem aus (TTS ist lokal auf dem Gerät)
 *
 * Verwendung:
 *   val tts = TtsManager(context)
 *   tts.speak("Hallo, ich bin dein Assistent.")
 *   tts.stop()       // laufende Ausgabe stoppen
 *   tts.shutdown()   // Ressourcen freigeben (in onDestroy)
 *
 * @param context     Application- oder CarContext. Muss gültig bleiben.
 * @param onReady     Wird aufgerufen sobald die TTS-Engine einsatzbereit ist.
 * @param onError     Optionaler Callback wenn TTS-Initialisierung fehlschlägt.
 */
class TtsManager(
    private val context: Context,
    private val onReady: (() -> Unit)? = null,
    private val onError: (() -> Unit)? = null,
) {

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_ID_PREFIX = "da_"
    }

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false

    init {
        initEngine()
    }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Liest [text] laut vor.
     *
     * [TextToSpeech.QUEUE_FLUSH] bricht jede laufende Ausgabe sofort ab
     * und beginnt mit dem neuen Text. Dadurch bleibt die App reaktionsschnell
     * (keine Warteschlange, kein Rückstau alter Sprachausgaben).
     *
     * Wird aufgerufen bevor die Engine bereit ist, wird der Aufruf ignoriert
     * und ein Warn-Log geschrieben.
     */
    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "speak() aufgerufen, aber TTS-Engine ist noch nicht bereit.")
            return
        }
        val utteranceId = UTTERANCE_ID_PREFIX + UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d(TAG, "TTS spricht [id=$utteranceId]: \"$text\"")
    }

    /**
     * Stoppt die aktuelle Sprachausgabe sofort.
     * Kein Fehler wenn TTS nicht spricht.
     */
    fun stop() {
        tts?.stop()
        Log.d(TAG, "TTS gestoppt.")
    }

    /**
     * Gibt alle Ressourcen frei.
     * Muss in onDestroy des Screens oder Services aufgerufen werden.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.d(TAG, "TtsManager heruntergefahren.")
    }

    /** Gibt zurück ob die TTS-Engine einsatzbereit ist. */
    fun isReady(): Boolean = isReady

    // ── Private Helpers ──────────────────────────────────────────────────────

    private fun initEngine() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.GERMAN)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "Deutsch nicht verfügbar – TTS-Sprache nicht gesetzt.")
                    // Fallback auf Systemsprache; TTS bleibt nutzbar
                } else {
                    Log.d(TAG, "TTS-Engine bereit, Sprache: Deutsch")
                }
                tts?.setOnUtteranceProgressListener(utteranceListener)
                isReady = true
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS-Initialisierung fehlgeschlagen (Status: $status)")
                isReady = false
                onError?.invoke()
            }
        }
    }

    // ── UtteranceProgressListener ────────────────────────────────────────────

    private val utteranceListener = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "TTS gestartet [id=$utteranceId]")
        }

        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "TTS abgeschlossen [id=$utteranceId]")
        }

        @Deprecated("Deprecated in API 21, aber für Abwärtskompatibilität überschrieben")
        override fun onError(utteranceId: String?) {
            Log.w(TAG, "TTS Fehler [id=$utteranceId]")
        }
    }
}
