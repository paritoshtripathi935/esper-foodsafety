package com.esper.foodsafety.sync

import android.util.Log
import com.esper.foodsafety.BuildConfig
import com.esper.foodsafety.data.remote.api.DeviceDto
import com.esper.foodsafety.data.remote.api.RetrofitClient
import com.esper.foodsafety.data.remote.api.SiteDto
import java.time.Instant

object DeviceRegistrar {
    private const val TAG = "DeviceRegistrar"

    suspend fun register(identity: DeviceIdentity.Identity) {
        val api    = RetrofitClient.create(BuildConfig.SUPABASE_URL)
        val apiKey = BuildConfig.SUPABASE_ANON_KEY
        val auth   = "Bearer $apiKey"

        runCatching {
            api.upsertSite(apiKey, auth, site = SiteDto(
                id   = identity.siteId,
                name = identity.siteName,
            ))
        }.onFailure { Log.w(TAG, "site upsert failed", it) }

        runCatching {
            api.upsertDevice(apiKey, auth, device = DeviceDto(
                id        = identity.deviceId,
                site_id   = identity.siteId,
                name      = identity.deviceName,
                kind      = "kiosk",
                last_seen = Instant.now().toString(),
            ))
        }.onFailure { throw it }

        Log.i(TAG, "registered ${identity.deviceId} @ ${identity.siteId}")
    }
}
