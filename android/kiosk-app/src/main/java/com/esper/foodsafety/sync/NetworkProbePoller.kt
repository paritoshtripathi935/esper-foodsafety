package com.esper.foodsafety.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls Supabase for the latest temp reading per station.
 * Returns a Map<stationId, tempF> — one entry for every station actively posting.
 * Stations that haven't posted in [staleAfterMs] are removed from the map.
 */
class NetworkProbePoller(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val pollIntervalMs: Long = 2_000L,
    private val staleAfterMs: Long = 10_000L,
) {
    // Map<station, tempF> — all actively sending probes
    private val _temperatures = MutableStateFlow<Map<String, Float>>(emptyMap())
    val temperatures: StateFlow<Map<String, Float>> = _temperatures.asStateFlow()

    // Single-probe compat shim (first station or null)
    val temperature: StateFlow<Float?> get() = _singleTemp
    private val _singleTemp = MutableStateFlow<Float?>(null)

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            _status.value = "Polling Supabase..."
            while (isActive) {
                try {
                    val map = fetchLatestPerStation()
                    _temperatures.value = map
                    _singleTemp.value = map.values.firstOrNull()
                    _status.value = if (map.isEmpty())
                        "Connected — no probes"
                    else
                        "Connected (${map.size} probe${if (map.size > 1) "s" else ""})"
                } catch (e: Exception) {
                    _status.value = "Poll error: ${e.message}"
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        _status.value = "Idle"
    }

    private fun fetchLatestPerStation(): Map<String, Float> {
        val url = URL("$supabaseUrl/rest/v1/rpc/latest_temp_per_station")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000

            val body = org.json.JSONObject()
                .put("stale_seconds", staleAfterMs / 1000L)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != 200) return emptyMap()

            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            val result = mutableMapOf<String, Float>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result[obj.getString("station")] = obj.getDouble("value").toFloat()
            }
            result
        } finally {
            conn.disconnect()
        }
    }
}
