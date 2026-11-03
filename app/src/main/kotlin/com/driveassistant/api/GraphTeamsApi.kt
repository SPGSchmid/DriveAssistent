package com.driveassistant.api

import com.driveassistant.data.model.teams.ChatListResponse
import com.driveassistant.data.model.teams.MessageListResponse
import com.driveassistant.data.model.teams.SendMessageRequest
import com.driveassistant.data.model.teams.TeamsMessage
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GraphTeamsApi
 * ═════════════
 * Retrofit-Interface für Microsoft Graph Teams-Endpunkte.
 *
 * Base URL: `https://graph.microsoft.com/v1.0/` (via [GraphApiClient])
 * Authentifizierung: Bearer-Token (via [AuthInterceptor] in [GraphApiClient])
 *
 * Benötigte Scopes: `Chat.ReadWrite`
 */
interface GraphTeamsApi {

    /**
     * Liefert alle Chats des angemeldeten Nutzers inkl. Mitglieder.
     */
    @GET("me/chats")
    suspend fun getChats(
        @Query("\$expand") expand: String = "members",
        @Query("\$top") top: Int = 20
    ): ChatListResponse

    /**
     * Liefert die letzten Nachrichten eines Chats, neueste zuerst.
     */
    @GET("chats/{chatId}/messages")
    suspend fun getChatMessages(
        @Path("chatId") chatId: String,
        @Query("\$top") top: Int = 10,
        @Query("\$orderby") orderBy: String = "createdDateTime desc"
    ): MessageListResponse

    /**
     * Sendet eine Nachricht in einen Chat.
     * Graph API gibt die neu erstellte Nachricht zurück.
     */
    @POST("chats/{chatId}/messages")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body message: SendMessageRequest
    ): TeamsMessage
}
