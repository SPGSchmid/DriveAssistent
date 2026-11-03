package com.driveassistant.data.model.teams

/**
 * SendMessageRequest
 * ══════════════════
 * Request-Body für das Senden einer Teams-Nachricht.
 *
 * Graph API: `POST /chats/{id}/messages`
 * Benötigte Scopes: `Chat.ReadWrite`
 */
data class SendMessageRequest(
    val body: MessageContent
)
