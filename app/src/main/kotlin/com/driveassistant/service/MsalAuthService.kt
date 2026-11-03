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
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException

object MsalAuthService {

    private const val TAG = "MsalAuthService"

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

    fun initialize(context: Context, callback: AuthInitCallback) {
        secureStorage = SecureStorage(context)

        PublicClientApplication.createSingleAccountPublicClientApplication(
            context.applicationContext,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
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

    fun signIn(activity: Activity, callback: AuthCallback) {
        val app = msalApp ?: run {
            callback.onError(IllegalStateException("MSAL nicht initialisiert."))
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
                    callback.onError(Exception("Login vom Nutzer abgebrochen"))
                }
            })
            .build()

        app.acquireToken(parameters)
    }

    fun signOut(callback: SimpleCallback) {
        val app = msalApp ?: run {
            callback.onError(IllegalStateException("MSAL nicht initialisiert."))
            return
        }

        app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {
                secureStorage?.remove(SecureStorage.KEY_MSAL_ACCESS_TOKEN)
                Log.d(TAG, "Sign-Out erfolgreich")
                callback.onComplete()
            }
            override fun onError(exception: MsalException) {
                Log.e(TAG, "Sign-Out fehlgeschlagen", exception)
                callback.onError(exception)
            }
        })
    }

    fun getAccessToken(callback: TokenCallback) {
        val app = msalApp ?: run {
            callback.onError(IllegalStateException("MSAL nicht initialisiert."))
            return
        }

        app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount == null) {
                    callback.onError(Exception("Kein Account angemeldet."))
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
                Log.d(TAG, "Account geaendert")
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "getCurrentAccount fehlgeschlagen", exception)
                callback.onError(exception)
            }
        })
    }

    fun isSignedIn(): Boolean {
        return secureStorage?.contains(SecureStorage.KEY_MSAL_ACCESS_TOKEN) == true
    }
}
