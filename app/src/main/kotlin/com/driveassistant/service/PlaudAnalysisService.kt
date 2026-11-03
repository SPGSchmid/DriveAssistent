package com.driveassistant.service

import android.content.Context
import android.util.Log
import com.driveassistant.api.OpenAiService
import com.driveassistant.data.model.ChatRequest
import com.driveassistant.data.model.Message
import com.driveassistant.util.SecureStorage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * PlaudAnalysisService
 * ════════════════════
 * Analysiert ein PLAUD-Gesprächstranskript on-device via Groq API
 * (gleicher OpenAI-kompatibler Endpunkt wie [OpenAiManager]).
 *
 * Der Groq-Prompt extrahiert:
 *  - Gesprächspartner-Name
 *  - 2-3 Satz Zusammenfassung auf Deutsch
 *  - Liste von Action Items / Follow-ups
 *  - Gesprächs-Stimmung (positive / neutral / negative)
 *
 * Das Ergebnis wird von [PlaudImportService] genutzt, um eine
 * formatierte HubSpot-Notiz zu erstellen.
 *
 * Sub-Task 3.2 – PLAUD-Transkript-Import in HubSpot
 */
class PlaudAnalysisService(context: Context) {

    /**
     * Ergebnis der KI-Transkript-Analyse.
     *
     * @param contactName  Erkannter Gesprächspartner (null wenn nicht identifizierbar)
     * @param summary      2-3 Satz Zusammenfassung des Gesprächs auf Deutsch
     * @param actionItems  Liste von Follow-up-Aufgaben aus dem Gespräch
     * @param sentiment    Gesprächs-Stimmung: "positive", "neutral" oder "negative"
     */
    data class TranscriptAnalysis(
        val contactName: String?,
        val summary: String,
        val actionItems: List<String>,
        val sentiment: String
    )

    companion object {
        private const val TAG = "PlaudAnalysisService"
        private const val BASE_URL = "https://api.groq.com/openai/"
        private const val MODEL = "llama-3.1-70b-versatile"
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val READ_TIMEOUT_SEC    = 60L  // Transkripte können lang sein

        private const val ANALYSIS_PROMPT = """Analysiere das folgende Gesprächstranskript eines Außendienstmitarbeiters.
Antworte NUR als valides JSON-Objekt ohne Markdown-Formatierung:
{
  "contactName": "Name des Gesprächspartners oder null",
  "summary": "2-3 Satz Zusammenfassung des Gesprächs auf Deutsch",
  "actionItems": ["Follow-up 1", "Angebot senden bis ..."],
  "sentiment": "positive/neutral/negative"
}

Transkript:
"""
    }

    private val secureStorage = SecureStorage(context)
    private val gson = Gson()
    private val service: OpenAiService by lazy { buildService() }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Analysiert [transcriptText] und gibt ein strukturiertes [TranscriptAnalysis] zurück.
     *
     * @throws IllegalStateException  wenn kein API-Key gespeichert ist
     * @throws IOException            bei Netzwerkproblemen oder API-Fehlern
     * @throws IllegalArgumentException wenn Groq kein valides JSON zurückgibt
     */
    suspend fun analyzeTranscript(transcriptText: String): TranscriptAnalysis {
        val prompt = ANALYSIS_PROMPT + transcriptText

        val request = ChatRequest(
            model = MODEL,
            messages = listOf(
                Message(role = "user", content = prompt)
            )
        )

        val response = service.chat(request)
        val rawJson = response.firstContent()
            ?: throw IOException("Groq hat leere Antwort für Transkript-Analyse zurückgegeben.")

        return parseAnalysis(rawJson)
    }

    // ── Internes Parsing ─────────────────────────────────────────────────────

    /**
     * Parst das JSON-Ergebnis der Groq-Antwort in ein [TranscriptAnalysis].
     *
     * Groq-Antworten können gelegentlich ein führendes/abschließendes Code-Fence
     * enthalten – diese werden vor dem Parsen entfernt.
     */
    private fun parseAnalysis(rawJson: String): TranscriptAnalysis {
        // Markdown-Fences entfernen falls vorhanden (z.B. ```json ... ```)
        val cleaned = rawJson
            .trimIndent()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val raw = gson.fromJson(cleaned, RawAnalysis::class.java)
                ?: throw IllegalArgumentException("Gson hat null zurückgegeben.")

            TranscriptAnalysis(
                contactName  = raw.contactName?.takeIf { it.isNotBlank() && it != "null" },
                summary      = raw.summary?.ifBlank { "Keine Zusammenfassung verfügbar." }
                               ?: "Keine Zusammenfassung verfügbar.",
                actionItems  = raw.actionItems ?: emptyList(),
                sentiment    = raw.sentiment?.lowercase()?.takeIf {
                               it in listOf("positive", "neutral", "negative")
                               } ?: "neutral"
            )
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Ungültiges JSON von Groq: $cleaned", e)
            throw IllegalArgumentException("Groq-Antwort ist kein valides JSON.", e)
        }
    }

    /** Internes Daten-Modell für Gson-Deserialisierung der Groq-Antwort. */
    private data class RawAnalysis(
        val contactName: String?,
        val summary: String?,
        val actionItems: List<String>?,
        val sentiment: String?
    )

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
