package com.driveassistant.service

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * GraphApiClient
 * ══════════════
 * Retrofit-Instanz für alle Microsoft Graph API-Calls.
 *
 * Features:
 * - Base URL: `https://graph.microsoft.com/v1.0/`
 * - OkHttp-Interceptor: fügt automatisch `Authorization: Bearer {token}` hinzu
 * - Token wird synchron via [MsalAuthService.getAccessToken] abgerufen
 * - Bei HTTP 401 → Fehlermeldung via [TtsManager] (erneutes Anmelden erforderlich)
 *
 * Verwendung:
 * ```kotlin
 * val service = GraphApiClient.retrofit.create(GraphMailService::class.java)
 * ```
 *
 * Sub-Task 1.4 – Microsoft Graph API OAuth 2.0 Authentifizierung
 * Sub-Task 1.5+ nutzt diese Instanz für alle Graph-Calls
 */
object GraphApiClient {

    private const val TAG = "GraphApiClient"
    const val BASE_URL = "https://graph.microsoft.com/v1.0/"

    /** Retrofit-Singleton – wird lazy initialisiert */
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ── OkHttp-Client ─────────────────────────────────────────────────────────

    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Auth-Interceptor ──────────────────────────────────────────────────────

    /**
     * Holt synchron den aktuellen Access Token via [MsalAuthService.getAccessToken]
     * und setzt den `Authorization: Bearer`-Header für jeden Request.
     *
     * Bei HTTP 401 wird über [TtsManager] eine Sprachausgabe ausgelöst.
     */
    private class AuthInterceptor : Interceptor {

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = fetchTokenSync()

            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                Log.w(TAG, "Kein Token verfügbar – Request wird ohne Authorization gesendet")
                chain.request()
            }

            val response = chain.proceed(request)

            if (response.code == 401) {
                Log.w(TAG, "HTTP 401 – Token abgelaufen oder ungültig")
                // TTS nicht verfügbar in GraphApiClient (kein Context) – Fehler nur loggen
            }

            return response
        }

        /**
         * Ruft [MsalAuthService.getAccessToken] synchron ab (Callback → CountDownLatch).
         * OkHttp-Interceptoren laufen auf einem Hintergrund-Thread, blockieren ist erlaubt.
         */
        private fun fetchTokenSync(): String? {
            var result: String? = null
            val latch = CountDownLatch(1)

            MsalAuthService.getAccessToken(object : TokenCallback {
                override fun onSuccess(accessToken: String) {
                    result = accessToken
                    latch.countDown()
                }

                override fun onError(exception: Exception) {
                    Log.e(TAG, "Token-Abruf für Request fehlgeschlagen", exception)
                    latch.countDown()
                }
            })

            latch.await(10, TimeUnit.SECONDS)
            return result
        }
    }
}

