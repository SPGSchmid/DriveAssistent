package com.driveassistant.api

import com.driveassistant.data.model.hubspot.ContactSearchResponse
import com.driveassistant.data.model.hubspot.CreateNoteRequest
import com.driveassistant.data.model.hubspot.DealSearchResponse
import com.driveassistant.data.model.hubspot.HubSpotNote
import com.driveassistant.data.model.hubspot.SearchRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * HubSpotApi
 * ══════════
 * Retrofit-Interface für die HubSpot CRM API v3.
 *
 * Base URL: https://api.hubapi.com/
 * Authentifizierung: Bearer Token via [HubSpotClient] (kein OAuth – Private App Token)
 *
 * Sub-Task 3.1 – HubSpot CRM Integration
 */
interface HubSpotApi {

    /**
     * Kontakt-Suche nach Name (Freitext-Suche über alle Kontaktfelder).
     */
    @POST("crm/v3/objects/contacts/search")
    suspend fun searchContacts(@Body request: SearchRequest): ContactSearchResponse

    /**
     * Alle Deals abrufen (mit den wichtigsten Eigenschaften).
     */
    @GET("crm/v3/objects/deals")
    suspend fun getDeals(
        @Query("limit") limit: Int = 10,
        @Query("properties") properties: String = "dealname,amount,dealstage,closedate,pipeline",
        @Query("associations") associations: String = "contacts"
    ): DealSearchResponse

    /**
     * Neue Notiz anlegen und optional mit einem Kontakt verknüpfen.
     */
    @POST("crm/v3/objects/notes")
    suspend fun createNote(@Body request: CreateNoteRequest): HubSpotNote
}
