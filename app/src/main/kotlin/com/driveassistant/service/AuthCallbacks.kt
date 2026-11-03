package com.driveassistant.service

/**
 * AuthCallback
 * ════════════
 * Ergebnis eines interaktiven Login-Flows via [MsalAuthService.signIn].
 */
interface AuthCallback {
    fun onSuccess(accessToken: String)
    fun onError(exception: Exception)
}

/**
 * TokenCallback
 * ═════════════
 * Ergebnis eines Silent-Token-Abrufs via [MsalAuthService.getAccessToken].
 */
interface TokenCallback {
    fun onSuccess(accessToken: String)
    fun onError(exception: Exception)
}

/**
 * SimpleCallback
 * ══════════════
 * Generisches Ergebnis für Operationen ohne Rückgabewert (z. B. Sign-Out).
 */
interface SimpleCallback {
    fun onComplete()
    fun onError(exception: Exception)
}

/**
 * AuthInitCallback
 * ════════════════
 * Ergebnis der MSAL-Initialisierung via [MsalAuthService.initialize].
 */
interface AuthInitCallback {
    fun onInitialized()
    fun onError(exception: Exception)
}
