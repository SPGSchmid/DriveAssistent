package com.driveassistant.data.model.teams

/**
 * TeamsChat
 * ═════════
 * Datenmodelle für Microsoft Graph Teams-Chat-Endpunkte.
 *
 * Graph API: `GET /me/chats?$expand=members`
 * Benötigte Scopes: `Chat.ReadWrite`
 */

data class ChatListResponse(
    val value: List<TeamsChat>
)

data class TeamsChat(
    val id: String,
    /** "oneOnOne", "group" oder "meeting" */
    val chatType: String,
    val topic: String?,
    val members: List<ChatMember>?
)

data class ChatMember(
    val id: String,
    val displayName: String?,
    val email: String?
)
