package com.driveassistant.data.model.teams

/**
 * TeamsMessage
 * ════════════
 * Datenmodelle für Microsoft Graph Teams-Nachrichten-Endpunkte.
 *
 * Graph API: `GET /chats/{id}/messages`
 * Benötigte Scopes: `Chat.ReadWrite`
 */

data class MessageListResponse(
    val value: List<TeamsMessage>
)

data class TeamsMessage(
    val id: String,
    val createdDateTime: String,
    val from: MessageSender?,
    val body: MessageContent
)

data class MessageSender(
    val user: MessageUser?
)

data class MessageUser(
    val displayName: String?,
    val id: String?
)

data class MessageContent(
    val content: String,
    val contentType: String
)
