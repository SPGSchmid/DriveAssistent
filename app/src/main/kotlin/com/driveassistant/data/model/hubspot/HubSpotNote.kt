package com.driveassistant.data.model.hubspot

data class CreateNoteRequest(
    val properties: NoteProperties,
    val associations: List<NoteAssociation>? = null
)

data class NoteProperties(
    val hs_note_body: String,
    val hs_timestamp: String   // ISO 8601 UTC: "2024-01-01T10:00:00.000Z"
)

data class NoteAssociation(
    val to: AssociationTo,
    val types: List<AssociationType>
)

data class AssociationTo(val id: String)

data class AssociationType(
    val associationCategory: String = "HUBSPOT_DEFINED",
    val associationTypeId: Int = 202
)

data class HubSpotNote(val id: String, val properties: NoteProperties)
