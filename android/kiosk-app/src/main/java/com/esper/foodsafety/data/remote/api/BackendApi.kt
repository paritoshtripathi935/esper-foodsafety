package com.esper.foodsafety.data.remote.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class EventDto(
    val device_id: String,
    val site_id: String,
    val station: String,
    val probe_id: String?,
    val type: String,
    val value: Double,
    val ts: String,
    val payload: Map<String, Any> = emptyMap(),
)

data class SiteDto(
    val id: String,
    val name: String,
)

data class DeviceDto(
    val id: String,
    val site_id: String,
    val name: String,
    val kind: String,
    val last_seen: String,
)

interface BackendApi {
    @POST("rest/v1/events")
    suspend fun insertEvents(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "return=minimal",
        @Body events: List<EventDto>,
    ): retrofit2.Response<Unit>

    @POST("rest/v1/sites")
    suspend fun upsertSite(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Body site: SiteDto,
    ): retrofit2.Response<Unit>

    @POST("rest/v1/devices")
    suspend fun upsertDevice(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Body device: DeviceDto,
    ): retrofit2.Response<Unit>
}
