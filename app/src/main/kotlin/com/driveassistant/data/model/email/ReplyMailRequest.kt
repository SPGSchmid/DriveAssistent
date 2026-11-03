package com.driveassistant.data.model.email

import com.google.gson.annotations.SerializedName

/**
 * ReplyMailRequest
 * ════════════════
 * Request-Body für `POST /me/messages/{id}/reply`.
 *
 * Graph API erwartet:
 * ```json
 * { "comment": "Antworttext" }
 * ```
 */
data class ReplyMailRequest(
    @SerializedName("comment")
    val comment: String,
)
