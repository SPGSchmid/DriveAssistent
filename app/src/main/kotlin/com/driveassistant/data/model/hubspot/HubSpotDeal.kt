package com.driveassistant.data.model.hubspot

data class DealSearchResponse(val results: List<HubSpotDeal>, val total: Int)

data class HubSpotDeal(
    val id: String,
    val properties: DealProperties
)

data class DealProperties(
    val dealname: String?,
    val amount: String?,
    val dealstage: String?,
    val closedate: String?,
    val pipeline: String?
)
