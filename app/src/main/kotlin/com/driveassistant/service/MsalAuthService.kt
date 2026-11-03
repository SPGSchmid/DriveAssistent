package com.driveassistant.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.driveassistant.R
import com.driveassistant.util.SecureStorage
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException

/**
 * MsalAuthService
 * ═══════════════
 * Kapselt die gesamte Microsoft Authentication Library (MSAL)-Logik.
 *
 * Verantwortlichkeiten:
 * - Initialisierung der [ISingleAccountPublicClientApplication] via `auth_config.json`
 * - Interaktiver Login-Flow per Browser-Tab ([signIn])
 * - Silent-Token-Refresh ohne UI ([getAccessToken])
 * - Token-Caching in [SecureStorage] unter [SecureStorage.KEY_MSAL_ACCESS_TOKEN]
 * - Sign-Out und Account-Entfernung ([signOut])
 *
 * Verwendung:
 * ```kotlin
 * MsalAuthService.initialize(context) { ... }
 * MsalAuthService.signIn(activity, callback)
 * MsalAuthService.getAccessToken(callback)
 * ```
 *
 * Sub-Task 1.4 – Microsoft Graph API OAuth 2.0 Authentifizierung
 */
object MsalAuthService {

    private const val TAG = "MsalAuthService"

    /**
     * Alle Scopes die beim Login beantragt werden.
     * Entsprechend der Berechtigungen in der Azure App-Registrierung.
     */
    val SCOPES = listOf(
        "User.Read",
        "Mail.ReadWrite",
        "Calendars.ReadWrite",
        "Chat.ReadWrite",
        "ChannelMessage.ReadWrite",
        "offline_access"
    )

    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var secureStorage: SecureStorage? = null

    // ── Initialisierung ───────────────────────────────────────────────────────

    /**
     * Initialisiert die MSAL-Instanz anhand von `res/raw/auth_config.json`.
     * Muss einmalig beim App-Start aufgerufen werden (z. B. in [DriveAssistantApp]).
     *
     * @param context Application-Context
     * @param callback Wird auf dem aufrufenden Thread zurückgerufen
     */
    fun initialize(context: Context, callback: AuthInitCallback) {
        secureStorage = SecureStorage(context)

        PublicClientApplication.createSingleAccountPublicClientApplication(
            context.applicationContext,
            R.raw.auth_config,
            object : PublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    msalApp = application
                    Log.d(TAG, "MSAL erfolgreich initialisiert")
                    callback.onInitialized()
                }

                override fun onError(exception: MsalException) {
                    Log.e(TAG, "MSAL Initialisierung fehlgeschlagen", exception)
                    callback.onError(exception)
                }
            }
        )
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Startet den interaktiven Login-Flow im Browser-Tab.
     * Kann nur von einer Activity aus aufgerufen werden (nicht aus einem Service).
     *
     * @param activity Aktuell sichtbare Activity (für Browser-Tab-Redirect)
     * @param callback Liefert den Access Token bei Erfolg
     */
    fun signIn(activity: Activity, callback: AuthCallback) {
        val app = msalApp ?: run {
            callback.onError(IllegalStateException("MSAL nicht initialisiert. initialize() zuerst aufrufen."))
            return
        }

        val parameters = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .withScopes(SCOPES)
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    val token = authenticationResult.accessToken
                    secureStorage?.saveString(SecureStorage.KEY_MSAL_ACCESS_TOKEN, token)
                    Log.d(TAG, "Login erfolgreich, Token gecacht")
                    callback.onSuccess(token)
                }

                override fun onError(exception: MsalException) {
                    Log.e(TAG, "Login fehlgeschlagen", exception)
                    callback.onError(exception)
                }

                override fun onCancel() {
                    Log.d(TAG, "Login abgebrochen vom Nutzer")
                    callback.onError(Exception("Login vom Nutzer abgebrochen"))
                }
            })
            .build()

        app.acquireToken(parameters)
    }

    // ── Sign-Out ──────────────────────────────────────────────────────────────

    /**
     * Entfernt den aktuell gespeicherten Account und löscht den gecachten Token.
     *
     * @param callback Wird nach abgeschlossenem Sign-Out aufgerufen
     */
    fun signOut(callback: SimpleCallback) {
        val app = msalApp ?: run {
            callback.onError(IllegalStateException("MSAL nicht initialisiert."))
            return
        }

        app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {
                secureStorage?.remove(SecureStorage.KEY_MSAL_ACCESS_TOKEN)
                Log.d(TAG, "Sign-Out erfolgreich, Token gelöscht")
                callback.onComplete()
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "Sign-Out fehlgeschlagen", exception)
                callback.onError(exception)
            }
        })
    }

    // ── Token abrufen (silent) ────────────────────────────────────────────────

    /**
     * Ruft den Access Token **ohne Browser-UI** ab (silent refresh via Refresh Token).
     * MSAL erneuert den Token automatisch wenn er abgelaufen ist.
     * Der erneuerte Token wird in [SecureStorage] gecacht.
     *
     * Bei [com.microsoft.identity.client.exception.MsalUiRequiredException] muss
     * [signIn] erneut aufgerufen werden (z. B. nach Passwort-Änderung).
     *
     * @param callback Liefert den (ggf. erneuerten) Access Token
     */
    fun getAccessToken(callback: TokenCallback) {
        val app = msalApp ?: run {
            callback.onError(IllegalStateException("MSAL nicht initialisiert."))
            return
        }

        app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount == null) {
                    callback.onError(Exception("Kein Account angemeldet. Bitte zuerst anmelden."))
                    return
                }

                val parameters = AcquireTokenSilentParameters.Builder()
                    .forAccount(activeAccount)
                    .fromAuthority(activeAccount.authority)
                    .withScopes(SCOPES)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            val token = authenticationResult.accessToken
                            secureStorage?.saveString(SecureStorage.KEY_MSAL_ACCESS_TOKEN, token)
                            Log.d(TAG, "Silent-Token-Refresh erfolgreich")
                            callback.onSuccess(token)
                        }

                        override fun onError(exception: MsalException) {
                            Log.e(TAG, "Silent-Token-Refresh fehlgeschlagen", exception)
                            callback.onError(exception)
                        }

                        override fun onCancel() {
                            callback.onError(Exception("Token-Refresh abgebrochen"))
                        }
                    })
                    .build()

                app.acquireTokenSilentAsync(parameters)
            }

            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                Log.d(TAG, "Account geändert")
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "getCurrentAccount fehlgeschlagen", exception)
                callback.onError(exception)
            }
        })
    }

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * Gibt `true` zurück wenn ein gecachter Access Token vorhanden ist.
     * Schnell-Check ohne Netzwerk – nutzt nur [SecureStorage].
     *
     * Hinweis: Der Token kann trotzdem abgelaufen sein. [getAccessToken] erneuert
     * ihn in diesem Fall automatisch via silent refresh.
     */
    fun isSignedIn(): Boolean {
        return secureStorage?.contains(SecureStorage.KEY_MSAL_ACCESS_TOKEN) == true
    }
}
