package com.esper.foodsafety.sync

import android.content.Context

object DeviceIdentity {
    private const val PREFS = "device_identity"
    private const val KEY_SITE_ID     = "site_id"
    private const val KEY_SITE_NAME   = "site_name"
    private const val KEY_DEVICE_ID   = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"

    data class Identity(
        val siteId: String,
        val siteName: String,
        val deviceId: String,
        val deviceName: String,
    )

    fun isConfigured(ctx: Context): Boolean =
        prefs(ctx).getString(KEY_SITE_ID, null) != null

    fun save(ctx: Context, identity: Identity) {
        prefs(ctx).edit()
            .putString(KEY_SITE_ID,     identity.siteId)
            .putString(KEY_SITE_NAME,   identity.siteName)
            .putString(KEY_DEVICE_ID,   identity.deviceId)
            .putString(KEY_DEVICE_NAME, identity.deviceName)
            .apply()
    }

    fun load(ctx: Context): Identity? {
        val p = prefs(ctx)
        val siteId = p.getString(KEY_SITE_ID, null) ?: return null
        return Identity(
            siteId     = siteId,
            siteName   = p.getString(KEY_SITE_NAME,   "") ?: "",
            deviceId   = p.getString(KEY_DEVICE_ID,   "") ?: "",
            deviceName = p.getString(KEY_DEVICE_NAME, "") ?: "",
        )
    }

    fun toSlug(name: String, prefix: String): String =
        "$prefix-${name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
