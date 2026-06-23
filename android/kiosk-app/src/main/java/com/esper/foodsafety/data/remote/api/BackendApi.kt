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

interface BackendApi {
    @POST("rest/v1/events")
    suspend fun insertEvent(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "return=minimal",
        @Body event: EventDto,
    )
}
