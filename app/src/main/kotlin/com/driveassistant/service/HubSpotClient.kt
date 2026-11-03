package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.api.HubSpotApi
import com.driveassistant.util.SecureStorage
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * HubSpotClient
 * ═════════════
 * Retrofit-Singleton für alle HubSpot CRM API-Calls.
 *
 * Features:
 * - Base URL: `https://api.hubapi.com/`
 * - OkHttp-Interceptor: liest Token aus [SecureStorage.KEY_HUBSPOT_TOKEN]
 *   und setzt `Authorization: Bearer {token}` für jeden Request
 * - Kein OAuth – HubSpot Private App Token ist statisch (kein Refresh nötig)
 *
 * Verwendung:
 * ```kotlin
 * HubSpotClient.init(context)
 * val api = HubSpotClient.api
 * ```
 *
 * Sub-Task 3.1 – HubSpot CRM Integration
 */
object HubSpotClient {

    private const val TAG = "HubSpotClient"
    const val BASE_URL = "https://api.hubapi.com/"

    private var secureStorage: SecureStorage? = null

    /** Muss einmal beim App-Start mit dem Application-Context aufgerufen werden. */
    fun init(context: Context) {
        secureStorage = SecureStorage(context.applicationContext)
    }

    /** Retrofit-Singleton – wird lazy initialisiert */
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** Fertig konfiguriertes [HubSpotApi]-Interface */
    val api: HubSpotApi by lazy {
        retrofit.create(HubSpotApi::class.java)
    }

    // ── OkHttp-Client ─────────────────────────────────────────────────────────

    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(BearerTokenInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Bearer-Token-Interceptor ──────────────────────────────────────────────

    /**
     * Liest den gespeicherten HubSpot Private App Token aus [SecureStorage]
     * und setzt den `Authorization: Bearer`-Header für jeden Request.
     *
     * Bei fehlendem Token wird der Request ohne Authorization-Header gesendet
     * (HubSpot antwortet mit HTTP 401, was im Calling-Code behandelt wird).
     */
    private class BearerTokenInterceptor : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val token = secureStorage?.getString(SecureStorage.KEY_HUBSPOT_TOKEN)

            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                Log.w(TAG, "Kein HubSpot-Token vorhanden – Request ohne Authorization")
                chain.request()
            }

            return chain.proceed(request)
        }
    }
}
