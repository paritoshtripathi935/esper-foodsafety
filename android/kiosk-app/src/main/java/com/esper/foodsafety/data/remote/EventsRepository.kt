package com.esper.foodsafety.data.remote

import com.esper.foodsafety.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteEvent(
    val id: String,
    val deviceId: String,
    val siteId: String,
    val station: String,
    val type: String,
    val value: String,
    val ts: String,
    val payload: JSONObject,
)

object EventsRepository {

    suspend fun fetchEvents(
        type: String? = null,
        station: String? = null,
        limit: Int = 100,
    ): List<RemoteEvent> = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val sb = StringBuilder("$base/rest/v1/events?order=ts.desc&limit=$limit&select=id,device_id,site_id,station,type,value,ts,payload")
        if (type != null) sb.append("&type=eq.$type")
        if (station != null) sb.append("&station=eq.$station")

        val conn = URL(sb.toString()).openConnection() as HttpURLConnection
        return@withContext try {
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000

            if (conn.responseCode == 200) {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RemoteEvent(
                        id = o.optString("id"),
                        deviceId = o.optString("device_id"),
                        siteId = o.optString("site_id"),
                        station = o.optString("station"),
                        type = o.optString("type"),
                        value = o.optString("value"),
                        ts = o.optString("ts"),
                        payload = o.optJSONObject("payload") ?: JSONObject(),
                    )
                }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchEventsResult(
        type: String? = null,
        limit: Int = 100,
    ): Result<List<RemoteEvent>> = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val sb = StringBuilder("$base/rest/v1/events?order=ts.desc&limit=$limit&select=id,device_id,site_id,station,type,value,ts,payload")
        if (type != null) sb.append("&type=eq.$type")

        val conn = URL(sb.toString()).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000

            if (conn.responseCode == 200) {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                Result.success((0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RemoteEvent(
                        id = o.optString("id"),
                        deviceId = o.optString("device_id"),
                        siteId = o.optString("site_id"),
                        station = o.optString("station"),
                        type = o.optString("type"),
                        value = o.optString("value"),
                        ts = o.optString("ts"),
                        payload = o.optJSONObject("payload") ?: JSONObject(),
                    )
                })
            } else {
                Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }
}
