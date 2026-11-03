package com.driveassistant.data.model

import com.google.gson.annotations.SerializedName

/**
 * ChatResponse
 * ════════════
 * Retrofit-Datenklasse für die Antwort der OpenAI Chat Completions API.
 *
 * Relevante Felder aus der API-Antwort:
 * {
 *   "id": "chatcmpl-...",
 *   "choices": [{ "message": { "role": "assistant", "content": "..." }, "finish_reason": "stop" }],
 *   "usage": { "prompt_tokens": 123, "completion_tokens": 45, "total_tokens": 168 }
 * }
 */
data class ChatResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("choices")
    val choices: List<Choice>,

    @SerializedName("usage")
    val usage: Usage?,
) {
    /** Gibt den Antwort-Text des ersten Choice zurück (der Normalfall). */
    fun firstContent(): String? = choices.firstOrNull()?.message?.content
}

data class Choice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("message")
    val message: Message,

    @SerializedName("finish_reason")
    val finishReason: String?,
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int,
)
