package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.api.OpenAiService
import com.driveassistant.data.model.AssistantIntent
import com.driveassistant.data.model.ChatRequest
import com.driveassistant.util.SecureStorage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAiManager
 * ═════════════
 * Zentraler Service für die Kommunikation mit der Groq Chat Completions API
 * (OpenAI-kompatibles Interface – Modell: llama-3.1-70b-versatile).
 *
 * Verantwortlichkeiten:
 *  - Initialisiert Retrofit + OkHttp mit dem Bearer-Token aus [SecureStorage]
 *  - Baut den [ChatRequest] mit dem aktuellen [SessionContext] auf
 *  - Parst die Groq-JSON-Antwort in ein [AssistantIntent]
 *  - Implementiert Exponential Backoff bei HTTP 429 (Rate Limit) und 5xx-Fehlern
 *
 * Fehlerbehandlung:
 *  - Kein API-Key gespeichert → wirft [IllegalStateException]
 *  - Ungültige JSON-Antwort → wirft [IllegalArgumentException]
 *  - Netzwerkfehler / API-Fehler nach Retries → wirft [IOException]
 *
 * @param context Application-Context für [SecureStorage]
 */
class OpenAiManager(context: Context) {

    companion object {
        private const val TAG = "OpenAiManager"
        private const val BASE_URL = "https://api.groq.com/openai/"
        private const val MODEL = "llama-3.1-70b-versatile"

        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1_000L   // 1 Sekunde
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val READ_TIMEOUT_SEC    = 30L
    }

    private val secureStorage = SecureStorage(context)
    private val gson = Gson()

    private val service: OpenAiService by lazy { buildService() }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Verarbeitet die Spracheingabe des Nutzers und gibt einen [AssistantIntent] zurück.
     *
     * Ablauf:
     *  1. Nutzer-Text in [sessionContext] aufnehmen
     *  2. GPT-4-Request mit dem kompletten Verlauf senden (Retry-Logik)
     *  3. Antwort-JSON in [AssistantIntent] parsen
     *  4. Assistenten-Antwort in [sessionContext] aufnehmen
     *
     * @throws IllegalStateException  wenn kein API-Key gespeichert ist
     * @throws IllegalArgumentException wenn GPT-4 kein valides JSON zurückgibt
     * @throws IOException bei Netzwerkproblemen oder API-Fehlern nach Retries
     */
    suspend fun processUserInput(
        userText: String,
        sessionContext: SessionContext,
    ): AssistantIntent {
        sessionContext.addUserMessage(userText)

        val request = ChatRequest(
            model = MODEL,
            messages = sessionContext.getMessages(),
        )

        val rawJson = sendWithRetry(request)

        val intent = parseIntent(rawJson)
        sessionContext.addAssistantMessage(rawJson)

        Log.i(TAG, "Intent erkannt: ${intent.intent}, speech: \"${intent.speech}\"")
        return intent
    }

    // ── Retry-Logik ──────────────────────────────────────────────────────────

    /**
     * Sendet [request] an GPT-4 mit Exponential Backoff.
     * Wiederholt bei HTTP 429 und 5xx (max. [MAX_RETRIES] Versuche).
     *
     * @return den Antwort-String (JSON-Inhalt des ersten Choice)
     * @throws IOException wenn alle Versuche fehlschlagen
     */
    private suspend fun sendWithRetry(request: ChatRequest): String {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = service.chat(request)
                val content = response.firstContent()
                    ?: throw IOException("Groq hat leeren Inhalt zurückgegeben.")
                return content
            } catch (e: HttpException) {
                lastException = e
                if (e.code() == 429 || e.code() >= 500) {
                    val backoffMs = INITIAL_BACKOFF_MS * (1L shl attempt) // 1s, 2s, 4s
                    Log.w(TAG, "HTTP ${e.code()} – Versuch ${attempt + 1}/$MAX_RETRIES. " +
                            "Backoff: ${backoffMs}ms")
                    delay(backoffMs)
                } else {
                    // Nicht-retriable HTTP-Fehler (z.B. 401 Unauthorized)
                    throw IOException("API-Fehler: HTTP ${e.code()} – ${e.message()}", e)
                }
            } catch (e: IOException) {
                lastException = e
                val backoffMs = INITIAL_BACKOFF_MS * (1L shl attempt)
                Log.w(TAG, "Netzwerkfehler – Versuch ${attempt + 1}/$MAX_RETRIES. " +
                        "Backoff: ${backoffMs}ms: ${e.message}")
                delay(backoffMs)
            }
        }

        throw IOException(
            "Groq-Anfrage nach $MAX_RETRIES Versuchen fehlgeschlagen.",
            lastException,
        )
    }

    // ── JSON-Parsing ─────────────────────────────────────────────────────────

    /**
     * Parst [rawJson] in ein [AssistantIntent].
     * @throws IllegalArgumentException wenn das JSON ungültig oder unvollständig ist
     */
    private fun parseIntent(rawJson: String): AssistantIntent {
        return try {
            gson.fromJson(rawJson, AssistantIntent::class.java)
                ?: throw IllegalArgumentException("Gson hat null zurückgegeben.")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Groq-Antwort ist kein valides JSON: $rawJson")
            throw IllegalArgumentException("Ungültige Groq-Antwort: kein valides JSON.", e)
        }
    }

    // ── Retrofit-Initialisierung ─────────────────────────────────────────────

    private fun buildService(): OpenAiService {
        val apiKey = secureStorage.getString(SecureStorage.KEY_OPENAI_API_KEY)
            ?: throw IllegalStateException(
                "Kein Groq API-Key gefunden. Bitte zuerst in den Einstellungen hinterlegen."
            )

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiService::class.java)
    }
}
