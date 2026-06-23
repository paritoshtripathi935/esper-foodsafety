package com.esper.foodsafety.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class NetworkProbePoller(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val pollIntervalMs: Long = 2_000L,
) {
    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()

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
                    val temp = fetchLatestTemp()
                    if (temp != null) {
                        _temperature.value = temp
                        _status.value = "Connected (Network)"
                    }
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

    private fun fetchLatestTemp(): Float? {
        val url = URL("$supabaseUrl/rest/v1/events?type=eq.temp&order=ts.desc&limit=1&select=value")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    arr.getJSONObject(0).getDouble("value").toFloat()
                } else null
            } else null
        } finally {
            conn.disconnect()
        }
    }
}
