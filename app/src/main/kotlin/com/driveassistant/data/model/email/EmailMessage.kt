package com.driveassistant.data.model.email

import com.google.gson.annotations.SerializedName

/**
 * EmailListResponse
 * ═════════════════
 * Graph API-Antwortformat für `GET /me/messages`.
 * Das `value`-Array enthält die Liste der E-Mails.
 */
data class EmailListResponse(
    @SerializedName("value")
    val value: List<EmailMessage>,
)

/**
 * EmailMessage
 * ════════════
 * Repräsentiert eine einzelne E-Mail aus der Microsoft Graph API.
 *
 * Felder entsprechen dem `$select`-Parameter in [GraphMailApi.getUnreadMessages].
 */
data class EmailMessage(
    @SerializedName("id")
    val id: String,

    @SerializedName("subject")
    val subject: String?,

    /** Erste 255 Zeichen des Nachrichtentexts – für TTS-Vorschau. */
    @SerializedName("bodyPreview")
    val bodyPreview: String?,

    @SerializedName("from")
    val from: Recipient?,

    @SerializedName("isRead")
    val isRead: Boolean,

    @SerializedName("receivedDateTime")
    val receivedDateTime: String,

    /** "normal", "high" oder "low" */
    @SerializedName("importance")
    val importance: String,
)

data class Recipient(
    @SerializedName("emailAddress")
    val emailAddress: EmailAddress,
)

data class EmailAddress(
    @SerializedName("name")
    val name: String?,

    @SerializedName("address")
    val address: String,
)
