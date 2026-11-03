package com.driveassistant.service

import android.content.Context
import android.content.Intent
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * VoiceRecognitionManager
 * ═══════════════════════
 * Kapselt den Android [SpeechRecognizer] mit [RecognitionListener] und stellt
 * eine einfache Callback-basierte API bereit.
 *
 * Wichtig (Android-Constraint):
 *   [SpeechRecognizer] MUSS auf dem Main Thread erstellt und verwendet werden.
 *   Der [mainHandler] stellt sicher, dass alle Aufrufe korrekt dispatcht werden.
 *
 * Verwendung:
 *   val manager = VoiceRecognitionManager(context) { text -> ... }
 *   manager.startListening()
 *   manager.stopListening()
 *   manager.destroy()   // in onDestroy/onStop aufrufen
 *
 * @param context  Application- oder CarContext (nicht-Activity). Muss gültig bleiben.
 * @param onResult Callback der im Main Thread mit dem erkannten Text aufgerufen wird.
 * @param onError  Optionaler Callback für Fehler (Fehlercode aus [SpeechRecognizer]).
 * @param onStateChanged  Optionaler Callback wenn sich isListening ändert.
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: ((Int) -> Unit)? = null,
    private val onStateChanged: ((isListening: Boolean) -> Unit)? = null,
) {

    companion object {
        private const val TAG = "VoiceRecognitionMgr"
        private const val LANGUAGE = "de-DE"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Lazy-Initialisierung erst beim ersten startListening()-Aufruf. */
    private var speechRecognizer: SpeechRecognizer? = null

    /** Noise-Suppressor-Session-ID, wird nach Start des Recognizers belegt. */
    private var noiseSuppressor: NoiseSuppressor? = null

    /** Öffentlich lesbarer Zustand. */
    var isListening: Boolean = false
        private set

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Startet die Spracherkennung.
     * Muss aus dem Main Thread aufgerufen werden (oder über [mainHandler] geposted).
     * Ein laufender Erkennungsvorgang wird zuerst gestoppt.
     */
    fun startListening() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "SpeechRecognizer nicht verfügbar auf diesem Gerät.")
                onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            // Vorheriger Recognizer wird freigegeben, dann neu erstellt,
            // damit keine internen Zustandsfehler auftreten.
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = buildRecognizerIntent()
            speechRecognizer?.startListening(intent)

            setListeningState(true)
            Log.d(TAG, "Spracherkennung gestartet (Sprache: $LANGUAGE)")
        }
    }

    /**
     * Stoppt die laufende Spracherkennung ohne das Ergebnis zu verwerfen.
     * [RecognitionListener.onResults] wird danach noch aufgerufen.
     */
    fun stopListening() {
        mainHandler.post {
            speechRecognizer?.stopListening()
            setListeningState(false)
            Log.d(TAG, "Spracherkennung gestoppt.")
        }
    }

    /**
     * Gibt alle Ressourcen frei.
     * Muss in onDestroy/onStop des Screens oder Services aufgerufen werden.
     */
    fun destroy() {
        mainHandler.post {
            releaseNoiseSuppressor()
            speechRecognizer?.destroy()
            speechRecognizer = null
            setListeningState(false)
            Log.d(TAG, "VoiceRecognitionManager freigegeben.")
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_RESULTS, true)
            // Keine UI-Anzeige (kein Popup-Dialog)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

    private fun setListeningState(listening: Boolean) {
        if (isListening != listening) {
            isListening = listening
            onStateChanged?.invoke(listening)
        }
    }

    private fun releaseNoiseSuppressor() {
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    // ── RecognitionListener ──────────────────────────────────────────────────

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech – Mikrofon aktiv")
            // NoiseSuppressor aktivieren, falls verfügbar (AudioSession-ID ist ab hier bekannt)
            if (NoiseSuppressor.isAvailable()) {
                try {
                    // Audio-Session-ID 0 nutzt den globalen globalen Session-Slot
                    noiseSuppressor = NoiseSuppressor.create(0)
                    noiseSuppressor?.enabled = true
                    Log.d(TAG, "NoiseSuppressor aktiviert")
                } catch (e: Exception) {
                    Log.w(TAG, "NoiseSuppressor konnte nicht aktiviert werden: ${e.message}")
                }
            }
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Lautstärke-Feedback – kein Action nötig für Build 1
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Roher Audio-Buffer – wird nicht verwendet
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech – Erkennung läuft noch")
            setListeningState(false)
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: Fehlercode $error")
            releaseNoiseSuppressor()
            setListeningState(false)
            onError?.invoke(error)
        }

        override fun onResults(results: Bundle?) {
            releaseNoiseSuppressor()
            setListeningState(false)

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestMatch = matches?.firstOrNull()
            if (!bestMatch.isNullOrBlank()) {
                Log.d(TAG, "onResults: \"$bestMatch\"")
                onResult(bestMatch)
            } else {
                Log.d(TAG, "onResults: Kein Ergebnis erkannt")
                onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Partial results werden für diesen Build ignoriert
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Benutzerdefinierte Events – nicht verwendet
        }
    }
}
