package com.esper.probesimulator.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object NetworkPoster {

    suspend fun postTempEvent(
        supabaseUrl: String,
        anonKey: String,
        tempF: Float,
        deviceId: String = "probe-sim-01",
        siteId: String = "site-eastgate",
        station: String = "walk-in-cooler-1",
        probeId: String = "probe-a",
    ): Boolean = withContext(Dispatchers.IO) {
        val ts = Instant.now().toString()
        val body = """{"device_id":"$deviceId","site_id":"$siteId","station":"$station","probe_id":"$probeId","type":"temp","value":$tempF,"ts":"$ts","payload":{}}"""

        val url = URL("$supabaseUrl/rest/v1/events")
        val conn = url.openConnection() as HttpURLConnection
        return@withContext try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            conn.responseCode == 201
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }
}
