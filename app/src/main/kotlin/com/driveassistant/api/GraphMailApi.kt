package com.driveassistant.api

import com.driveassistant.data.model.email.EmailListResponse
import com.driveassistant.data.model.email.ReplyMailRequest
import com.driveassistant.data.model.email.SendMailRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GraphMailApi
 * ════════════
 * Retrofit-Interface für Microsoft Graph Mail-Endpunkte.
 *
 * Base URL: `https://graph.microsoft.com/v1.0/` (via [GraphApiClient])
 * Authentifizierung: Bearer-Token (via [AuthInterceptor] in [GraphApiClient])
 *
 * Benötigte Scopes: `Mail.ReadWrite`
 */
interface GraphMailApi {

    /**
     * Liefert ungelesene E-Mails, neueste zuerst.
     * OData-Parameter filtern, sortieren und begrenzen das Ergebnis serverseitig.
     */
    @GET("me/messages")
    suspend fun getUnreadMessages(
        @Query("\$filter")  filter: String  = "isRead eq false",
        @Query("\$orderby") orderBy: String = "receivedDateTime desc",
        @Query("\$top")     top: Int        = 10,
        @Query("\$select")  select: String  = "id,subject,bodyPreview,from,isRead,receivedDateTime,importance",
    ): EmailListResponse

    /**
     * Antwortet auf eine bestehende E-Mail.
     * Graph API gibt bei Erfolg HTTP 202 (Accepted) ohne Body zurück.
     */
    @POST("me/messages/{id}/reply")
    suspend fun replyToMessage(
        @Path("id")  messageId: String,
        @Body        body: ReplyMailRequest,
    ): Response<Unit>

    /**
     * Sendet eine neue E-Mail.
     * Graph API gibt bei Erfolg HTTP 202 (Accepted) ohne Body zurück.
     */
    @POST("me/sendMail")
    suspend fun sendMail(
        @Body request: SendMailRequest,
    ): Response<Unit>

    /**
     * Markiert eine E-Mail als gelesen.
     * Body: `{"isRead": true}`
     */
    @PATCH("me/messages/{id}")
    suspend fun markAsRead(
        @Path("id") messageId: String,
        @Body       body: Map<String, Boolean>,
    ): Response<Unit>
}
