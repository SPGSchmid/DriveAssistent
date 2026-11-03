package com.driveassistant.data.model.hubspot

data class SearchRequest(
    val query: String,
    val limit: Int = 5,
    val properties: List<String> = emptyList()
)
