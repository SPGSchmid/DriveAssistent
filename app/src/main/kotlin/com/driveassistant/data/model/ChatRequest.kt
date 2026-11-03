package com.driveassistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * ChatRequest
 * ═══════════
 * Retrofit-Datenklasse für POST https://api.openai.com/v1/chat/completions
 *
 * @param model          Modell-ID, z. B. "gpt-4o"
 * @param messages       Gesprächsverlauf (System-Prompt + Nutzer-/Assistenten-Nachrichten)
 * @param responseFormat Erzwingt JSON-Antwort: {"type": "json_object"}
 */
data class ChatRequest(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<Message>,

    @SerializedName("response_format")
    val responseFormat: ResponseFormat = ResponseFormat(),
)

data class Message(
    @SerializedName("role")
    val role: String,     // "system" | "user" | "assistant"

    @SerializedName("content")
    val content: String,
)

data class ResponseFormat(
    @SerializedName("type")
    val type: String = "json_object",
)
