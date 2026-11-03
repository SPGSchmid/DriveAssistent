package com.driveassistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CachedEmail
 * ════════════
 * Room-Entity für den Offline-Cache ungelesener E-Mails.
 *
 * Einträge älter als 24 Stunden werden beim nächsten Online-Abruf
 * automatisch über [EmailDao.deleteOlderThan] entfernt.
 */
@Entity(tableName = "cached_emails")
data class CachedEmail(
    @PrimaryKey val id: String,
    val subject: String?,
    val bodyPreview: String?,
    val fromName: String?,
    val fromAddress: String,
    val isRead: Boolean,
    val receivedDateTime: String,
    val importance: String,
    /** Unix-Timestamp (ms) des Zeitpunkts, an dem die Mail gecacht wurde. */
    val cachedAt: Long = System.currentTimeMillis(),
)
