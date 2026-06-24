package com.esper.probesimulator.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object NetworkPoster {

    suspend fun postTempEvent(
        supabaseUrl: String,
        anonKey: String,
        tempF: Float,
        deviceId: String,
        siteId: String,
        station: String,
        probeId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val ts   = Instant.now().toString()
        val body = """{"device_id":"$deviceId","site_id":"$siteId","station":"$station","probe_id":"$probeId","type":"temp","value":$tempF,"ts":"$ts","payload":{}}"""
        post("$supabaseUrl/rest/v1/events", anonKey, body) == 201
    }

    suspend fun upsertDevice(
        supabaseUrl: String,
        anonKey: String,
        deviceId: String,
        siteId: String,
        deviceName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("id",        deviceId)
            put("site_id",   siteId)
            put("name",      deviceName)
            put("kind",      "probe-sim")
            put("last_seen", Instant.now().toString())
        }.toString()
        val url = "$supabaseUrl/rest/v1/devices"
        val conn = openConn(url, anonKey)
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        return@withContext try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            conn.responseCode in 200..204
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    // Returns list of (id, name) pairs from the sites table.
    suspend fun fetchSites(
        supabaseUrl: String,
        anonKey: String,
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val url  = "$supabaseUrl/rest/v1/sites?select=id,name&order=name"
        val conn = (URL(url).openConnection() as HttpURLConnection).also { c ->
            c.requestMethod = "GET"
            c.setRequestProperty("apikey",        anonKey)
            c.setRequestProperty("Authorization", "Bearer $anonKey")
            c.setRequestProperty("Accept",        "application/json")
            c.connectTimeout = 5_000
            c.readTimeout    = 5_000
        }
        return@withContext try {
            if (conn.responseCode != 200) return@withContext emptyList()
            val json  = conn.inputStream.bufferedReader().readText()
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                obj.getString("id") to obj.getString("name")
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private fun post(url: String, anonKey: String, body: String): Int {
        val conn = openConn(url, anonKey)
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            conn.responseCode
        } catch (_: Exception) {
            -1
        } finally {
            conn.disconnect()
        }
    }

    private fun openConn(url: String, anonKey: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).also { c ->
            c.requestMethod = "POST"
            c.setRequestProperty("apikey",        anonKey)
            c.setRequestProperty("Authorization", "Bearer $anonKey")
            c.setRequestProperty("Content-Type",  "application/json")
            c.doOutput       = true
            c.connectTimeout = 5_000
            c.readTimeout    = 5_000
        }
}
