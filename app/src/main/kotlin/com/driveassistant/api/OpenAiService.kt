package com.driveassistant.api

import com.driveassistant.data.model.ChatRequest
import com.driveassistant.data.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * OpenAiService
 * ═════════════
 * Retrofit-Interface für die OpenAI Chat Completions API.
 *
 * Base URL: https://api.openai.com/
 * Endpunkt: POST v1/chat/completions
 *
 * Der Authorization-Header (Bearer-Token) wird vom OkHttp-Interceptor
 * in [com.driveassistant.service.OpenAiManager] automatisch gesetzt.
 */
interface OpenAiService {

    @POST("v1/chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}
