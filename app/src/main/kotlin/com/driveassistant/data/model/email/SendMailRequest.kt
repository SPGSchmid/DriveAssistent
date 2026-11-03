package com.driveassistant.data.model.email

import com.google.gson.annotations.SerializedName

/**
 * SendMailRequest
 * ═══════════════
 * Request-Body für `POST /me/sendMail`.
 *
 * Graph API erwartet folgende Struktur:
 * ```json
 * {
 *   "message": {
 *     "subject": "...",
 *     "body": { "contentType": "Text", "content": "..." },
 *     "toRecipients": [{ "emailAddress": { "name": "...", "address": "..." } }]
 *   },
 *   "saveToSentItems": true
 * }
 * ```
 */
data class SendMailRequest(
    @SerializedName("message")
    val message: NewMessage,

    @SerializedName("saveToSentItems")
    val saveToSentItems: Boolean = true,
)

data class NewMessage(
    @SerializedName("subject")
    val subject: String,

    @SerializedName("body")
    val body: MessageBody,

    @SerializedName("toRecipients")
    val toRecipients: List<Recipient>,
)

data class MessageBody(
    @SerializedName("contentType")
    val contentType: String = "Text",

    @SerializedName("content")
    val content: String,
)
