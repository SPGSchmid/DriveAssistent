package com.driveassistant.data.model.hubspot

data class ContactSearchResponse(
    val results: List<HubSpotContact>,
    val total: Int
)

data class HubSpotContact(
    val id: String,
    val properties: ContactProperties
)

data class ContactProperties(
    val firstname: String?,
    val lastname: String?,
    val email: String?,
    val phone: String?,
    val company: String?,
    val address: String?,
    val city: String?,
    val jobtitle: String?
)
