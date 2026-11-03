package com.driveassistant.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.driveassistant.service.IntentResultCallback
import com.driveassistant.service.IntentRouter
import com.driveassistant.service.OpenAiManager
import com.driveassistant.service.SessionContext
import com.driveassistant.service.TtsManager
import com.driveassistant.service.VoiceRecognitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HomeScreen
 * ══════════
 * Der initiale Startbildschirm des DriveAssistant im Android Auto-Display.
 *
 * Design-Prinzip (Android Auto Fahrersicherheit):
 *   - Minimal Text: nur absolut notwendige Informationen auf dem Display
 *   - Keine Touch-Eingabefelder: alle Interaktionen über Action / ActionStrip
 *   - Mikrofon ist die primäre Interaktion (ActionStrip oben rechts)
 *
 * Aufbau:
 *   ┌────────────────────────────────────┐
 *   │  [🎙️]                              │  ← ActionStrip (Mikrofon)
 *   │                                    │
 *   │   DriveAssistant                   │
 *   │   Tippe zum Sprechen               │  ← Status: bereit
 *   │   Ich höre zu...                   │  ← Status: isListening
 *   │   Verarbeite...                    │  ← Status: processing
 *   │                                    │
 *   └────────────────────────────────────┘
 *
 * Sub-Task 1.3 ergänzt GPT-4-Verarbeitung nach der Spracheingabe.
 */
class HomeScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        private const val TAG = "HomeScreen"
        private const val MSG_OFFLINE =
            "Keine Internetverbindung. Offline-Daten werden verwendet."
    }

    // ── Status-State ─────────────────────────────────────────────────────────

    private enum class UiState {
        READY, LISTENING, PROCESSING
    }

    private var uiState: UiState = UiState.READY

    // ── Coroutine-Scope (Screen hat kein lifecycleScope) ─────────────────────

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Manager ──────────────────────────────────────────────────────────────

    private val ttsManager: TtsManager = TtsManager(
        context = carContext.applicationContext,
        onReady = { onTtsReady() },
        onError = { Log.e(TAG, "TTS-Initialisierung fehlgeschlagen") },
    )

    private val openAiManager: OpenAiManager = OpenAiManager(carContext.applicationContext)
    private val sessionContext: SessionContext = SessionContext()
    private val intentRouter: IntentRouter = IntentRouter(carContext.applicationContext)

    private val voiceManager: VoiceRecognitionManager = VoiceRecognitionManager(
        context = carContext.applicationContext,
        onResult = { text -> onSpeechResult(text) },
        onError = { code -> onSpeechError(code) },
        onStateChanged = { listening ->
            if (!listening && uiState == UiState.LISTENING) {
                // Mikrofon hat aufgehört zu lauschen → Verarbeitung beginnt
                uiState = UiState.PROCESSING
                invalidate()
            }
        },
    )

    // ── Screen-Lifecycle ─────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val micAction = buildMicrophoneAction()
        val actionStrip = ActionStrip.Builder()
            .addAction(micAction)
            .build()

        return MessageTemplate.Builder(getStatusMessage())
            .setTitle(carContext.getString(R.string.app_name))
            .setActionStrip(actionStrip)
            .build()
    }

    /**
     * Ressourcen freigeben wenn der Screen verlassen wird.
     * Android Auto ruft dies auf wenn zurück navigiert oder Session getrennt wird.
     */
    override fun onStop() {
        voiceManager.destroy()
        ttsManager.shutdown()
        scope.cancel()
    }

    // ── TTS-Callback ─────────────────────────────────────────────────────────

    /**
     * Wird aufgerufen sobald die TTS-Engine einsatzbereit ist.
     * Hier erfolgt der Offline-Check: falls kein Netz vorhanden ist,
     * informiert TTS den Nutzer.
     */
    private fun onTtsReady() {
        if (!isNetworkAvailable()) {
            Log.i(TAG, "Kein Netzwerk – TTS-Offline-Hinweis wird ausgegeben.")
            ttsManager.speak(MSG_OFFLINE)
        }
    }

    // ── STT-Callbacks ────────────────────────────────────────────────────────

    /**
     * Wird aufgerufen wenn die Spracherkennung einen Text erkannt hat.
     * In Sub-Task 1.3 wird hier [gptService.processQuery(text)] aufgerufen.
     */
    private fun onSpeechResult(text: String) {
        Log.d(TAG, "Erkannter Text: \"$text\"")
        uiState = UiState.PROCESSING
        invalidate()

        scope.launch {
            try {
                val intent = openAiManager.processUserInput(text, sessionContext)
                intentRouter.route(
                    intent,
                    object : IntentResultCallback {
                        override fun onSpeechReady(text: String) {
                            uiState = UiState.READY
                            invalidate()
                            ttsManager.speak(text)
                        }

                        override fun onError(message: String) {
                            Log.e(TAG, "IntentRouter-Fehler: $message")
                            uiState = UiState.READY
                            invalidate()
                            ttsManager.speak(
                                carContext.getString(R.string.error_ai_processing)
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "GPT-4-Fehler: ${e.message}", e)
                uiState = UiState.READY
                invalidate()
                ttsManager.speak(
                    carContext.getString(R.string.error_ai_processing)
                )
            }
        }
    }

    private fun onSpeechError(errorCode: Int) {
        Log.w(TAG, "Spracherkennungsfehler: $errorCode")
        uiState = UiState.READY
        invalidate()

        val msg = when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH -> carContext.getString(R.string.error_no_match)
            SpeechRecognizer.ERROR_NETWORK  -> carContext.getString(R.string.error_network)
            else                            -> carContext.getString(R.string.error_generic)
        }
        CarToast.makeText(carContext, msg, CarToast.LENGTH_SHORT).show()
    }

    // ── Mikrofon-Interaktion ──────────────────────────────────────────────────

    /**
     * Wird aufgerufen wenn der Nutzer auf den Mikrofon-Button tippt.
     * Toggle: startet oder stoppt die Spracherkennung.
     */
    private fun onMicrophoneClicked() {
        when (uiState) {
            UiState.LISTENING -> {
                voiceManager.stopListening()
                uiState = UiState.READY
                invalidate()
            }
            UiState.READY, UiState.PROCESSING -> {
                // Laufende TTS-Ausgabe unterbrechen, dann zuhören
                ttsManager.stop()
                uiState = UiState.LISTENING
                invalidate()
                voiceManager.startListening()
                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.toast_listening),
                    CarToast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Erstellt die Mikrofon-Action für den ActionStrip.
     * Icon-Tint: Rot während Aufnahme, Standard sonst.
     */
    private fun buildMicrophoneAction(): Action {
        val icon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_btn_speak_now)
        ).apply {
            if (uiState == UiState.LISTENING) setTint(CarColor.RED)
        }.build()

        return Action.Builder()
            .setIcon(icon)
            .setTitle(
                when (uiState) {
                    UiState.LISTENING  -> carContext.getString(R.string.action_listening)
                    else               -> carContext.getString(R.string.action_speak)
                }
            )
            .setOnClickListener { onMicrophoneClicked() }
            .build()
    }

    /**
     * Gibt die Statusnachricht zurück, die im [MessageTemplate] angezeigt wird.
     */
    private fun getStatusMessage(): String = when (uiState) {
        UiState.READY       -> carContext.getString(R.string.status_ready)
        UiState.LISTENING   -> carContext.getString(R.string.status_listening)
        UiState.PROCESSING  -> carContext.getString(R.string.status_processing)
    }

    /**
     * Prüft ob eine aktive Netzwerkverbindung vorhanden ist.
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = carContext.getSystemService(CarContext.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
