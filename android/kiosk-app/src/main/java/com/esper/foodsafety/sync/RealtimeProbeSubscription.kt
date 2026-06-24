package com.esper.foodsafety.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "RealtimeProbe"
private const val STALE_MS = 30_000L
private const val HEARTBEAT_INTERVAL_MS = 30_000L
private const val MAX_BACKOFF_MS = 30_000L

/**
 * Supabase Realtime WebSocket subscription for live `temp` events.
 *
 * Connects to the Phoenix/Realtime endpoint, joins the postgres_changes
 * channel filtered to `type=eq.temp`, and maintains a Map<station, tempF>
 * with 30-second staleness eviction.
 *
 * Reconnects with exponential backoff (1 s → 2 s → 4 s … capped at 30 s).
 * Falls back gracefully — callers can watch [status] for "Disconnected" and
 * switch to [NetworkProbePoller] if desired.
 */
class RealtimeProbeSubscription(
    private val supabaseUrl: String,
    private val anonKey: String,
) {
    private val _temperatures = MutableStateFlow<Map<String, Float>>(emptyMap())
    val temperatures: StateFlow<Map<String, Float>> = _temperatures.asStateFlow()

    private val _status = MutableStateFlow("Connecting")
    val status: StateFlow<String> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS) // we send heartbeats manually
        .build()

    private var webSocket: WebSocket? = null
    private var connectJob: Job? = null
    private val refCounter = AtomicInteger(1)

    // station -> (tempF, lastSeenMs)
    private val stationData = java.util.concurrent.ConcurrentHashMap<String, Pair<Float, Long>>()

    fun start() {
        connectJob?.cancel()
        connectJob = scope.launch {
            launch { stalenessLoop() }
            connectWithBackoff()
        }
    }

    fun stop() {
        connectJob?.cancel()
        webSocket?.close(1000, "stop")
        webSocket = null
        _status.value = "Idle"
    }

    private suspend fun connectWithBackoff() {
        var backoffMs = 1_000L
        while (true) {
            _status.value = "Connecting"
            val connected = connect()
            if (connected) {
                backoffMs = 1_000L
            } else {
                _status.value = "Disconnected — fallback to poll"
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /** Returns true when the socket closes normally (so backoff resets), false on immediate failure. */
    private suspend fun connect(): Boolean {
        val wsUrl = supabaseUrl
            .trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://") +
            "/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        val latch = CompletableDeferred<Boolean>()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened")
                webSocket = ws
                _status.value = "Connected (realtime)"
                sendJoin(ws)
                scope.launch { heartbeatLoop(ws) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed $code $reason")
                _status.value = "Disconnected — fallback to poll"
                latch.complete(true) // closed normally — reset backoff
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                _status.value = "Disconnected — fallback to poll"
                latch.complete(false) // failure — trigger backoff
            }
        }

        client.newWebSocket(request, listener)
        return latch.await()
    }

    private fun sendJoin(ws: WebSocket) {
        val payload = JSONObject()
            .put("config", JSONObject()
                .put("postgres_changes", org.json.JSONArray()
                    .put(JSONObject()
                        .put("event", "INSERT")
                        .put("schema", "public")
                        .put("table", "events")
                        .put("filter", "type=eq.temp")
                    )
                )
            )
        val frame = JSONObject()
            .put("topic", "realtime:public:events")
            .put("event", "phx_join")
            .put("payload", payload)
            .put("ref", refCounter.getAndIncrement().toString())
        ws.send(frame.toString())
    }

    private suspend fun heartbeatLoop(ws: WebSocket) {
        while (true) {
            delay(HEARTBEAT_INTERVAL_MS)
            if (webSocket !== ws) break
            val frame = JSONObject()
                .put("topic", "phoenix")
                .put("event", "heartbeat")
                .put("payload", JSONObject())
                .put("ref", refCounter.getAndIncrement().toString())
            ws.send(frame.toString())
        }
    }

    private suspend fun stalenessLoop() {
        while (true) {
            delay(5_000L)
            val now = System.currentTimeMillis()
            val evicted = stationData.entries.filter { now - it.value.second > STALE_MS }.map { it.key }
            if (evicted.isNotEmpty()) {
                evicted.forEach { stationData.remove(it) }
                publishMap()
            }
        }
    }

    private fun handleFrame(text: String) {
        try {
            val obj = JSONObject(text)
            val event = obj.optString("event")
            if (event != "postgres_changes") return

            val record = obj
                .optJSONObject("payload")
                ?.optJSONObject("data")
                ?.optJSONObject("record") ?: return

            val station = record.optString("station").takeIf { it.isNotBlank() } ?: return
            val value   = record.optDouble("value", Double.NaN).takeIf { !it.isNaN() }?.toFloat() ?: return

            stationData[station] = value to System.currentTimeMillis()
            publishMap()
        } catch (e: Exception) {
            Log.w(TAG, "Frame parse error", e)
        }
    }

    private fun publishMap() {
        _temperatures.value = stationData.mapValues { it.value.first }
    }
}
