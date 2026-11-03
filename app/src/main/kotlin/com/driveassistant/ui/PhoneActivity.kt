package com.driveassistant.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.driveassistant.R
import com.driveassistant.service.AuthCallback
import com.driveassistant.service.MsalAuthService
import com.driveassistant.service.TtsManager
import com.driveassistant.util.SecureStorage

/**
 * PhoneActivity
 * ═════════════
 * Pflicht-Activity für Android Auto Apps.
 *
 * Zeigt auf dem Smartphone den Anmelde-Status und einen Login-Button.
 * Der interaktive Microsoft-Login-Flow kann nur in einer Activity gestartet
 * werden (nicht aus einem Service oder Auto-Display).
 *
 * Ablauf beim Start:
 * 1. [MsalAuthService.isSignedIn] prüft ob bereits ein Token gecacht ist
 * 2. Falls ja → Hinweis "Angemeldet als Microsoft 365-Nutzer"
 * 3. Falls nein → Login-Button wird angezeigt
 * 4. Nach erfolgreichem Login → TTS: "Anmeldung erfolgreich."
 */
class PhoneActivity : AppCompatActivity() {

    private lateinit var secureStorage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)

        if (MsalAuthService.isSignedIn()) {
            showSignedInUi()
        } else {
            showLoginUi()
        }
    }

    // ── UI: Angemeldet ────────────────────────────────────────────────────────

    private fun showSignedInUi() {
        val layout = buildLayout()

        val statusText = TextView(this).apply {
            text = getString(R.string.phone_activity_signed_in)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        // ── Groq API Key Eingabe ──────────────────────────────────────────────
        val apiKeyLabel = TextView(this).apply {
            text = "Groq API Key"
            textSize = 14f
            setPadding(0, 48, 0, 8)
        }

        val currentKey = secureStorage.getString(SecureStorage.KEY_OPENAI_API_KEY)
        val apiKeyInput = EditText(this).apply {
            hint = "gsk_..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(if (currentKey != null) "●".repeat(12) else "")
        }

        val saveKeyButton = Button(this).apply {
            text = if (currentKey != null) "API Key aktualisieren" else "API Key speichern"
            setOnClickListener {
                val input = apiKeyInput.text.toString().trim()
                if (input.isNotEmpty() && !input.contains("●")) {
                    secureStorage.saveString(SecureStorage.KEY_OPENAI_API_KEY, input)
                    Toast.makeText(this@PhoneActivity, "✓ Groq API Key gespeichert", Toast.LENGTH_SHORT).show()
                    apiKeyInput.setText("●".repeat(12))
                    text = "API Key aktualisieren"
                }
            }
        }

        // ── HubSpot Private App Token Eingabe ─────────────────────────────────
        val hubSpotLabel = TextView(this).apply {
            text = "HubSpot Private App Token"
            textSize = 14f
            setPadding(0, 48, 0, 8)
        }

        val currentHubSpotToken = secureStorage.getString(SecureStorage.KEY_HUBSPOT_TOKEN)
        val hubSpotInput = EditText(this).apply {
            hint = "pat-eu1-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(if (currentHubSpotToken != null) "●".repeat(12) else "")
        }

        val saveHubSpotButton = Button(this).apply {
            text = if (currentHubSpotToken != null) "Token aktualisieren" else "Token speichern"
            setOnClickListener {
                val input = hubSpotInput.text.toString().trim()
                if (input.isNotEmpty() && !input.contains("●")) {
                    secureStorage.saveString(SecureStorage.KEY_HUBSPOT_TOKEN, input)
                    Toast.makeText(this@PhoneActivity, "✓ HubSpot Token gespeichert", Toast.LENGTH_SHORT).show()
                    hubSpotInput.setText("●".repeat(12))
                    text = "Token aktualisieren"
                }
            }
        }

        val signOutButton = Button(this).apply {
            text = getString(R.string.phone_activity_sign_out)
            setOnClickListener { onSignOutClicked() }
        }

        layout.addView(statusText)
        layout.addView(apiKeyLabel)
        layout.addView(apiKeyInput)
        layout.addView(saveKeyButton)
        layout.addView(hubSpotLabel)
        layout.addView(hubSpotInput)
        layout.addView(saveHubSpotButton)
        layout.addView(signOutButton)
        setContentView(layout)
    }

    // ── UI: Nicht angemeldet ──────────────────────────────────────────────────

    private fun showLoginUi() {
        val layout = buildLayout()

        val infoText = TextView(this).apply {
            text = getString(R.string.phone_activity_message)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val loginButton = Button(this).apply {
            text = getString(R.string.phone_activity_sign_in)
            setOnClickListener { onLoginClicked() }
        }

        layout.addView(infoText)
        layout.addView(loginButton)
        setContentView(layout)
    }

    // ── Event-Handler ─────────────────────────────────────────────────────────

    private fun onLoginClicked() {
        MsalAuthService.signIn(this, object : AuthCallback {
            override fun onSuccess(accessToken: String) {
                TtsManager.speak("Anmeldung erfolgreich.")
                runOnUiThread { showSignedInUi() }
            }

            override fun onError(exception: Exception) {
                TtsManager.speak("Anmeldung fehlgeschlagen. Bitte versuche es erneut.")
            }
        })
    }

    private fun onSignOutClicked() {
        MsalAuthService.signOut(object : com.driveassistant.service.SimpleCallback {
            override fun onComplete() {
                runOnUiThread { showLoginUi() }
            }

            override fun onError(exception: Exception) {
                // Sign-Out-Fehler sind für den Nutzer nicht kritisch
            }
        })
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private fun buildLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(64, 64, 64, 64)
    }
}
