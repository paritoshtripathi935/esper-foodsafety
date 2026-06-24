package com.esper.foodsafety.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-process queue that persists unsynced events to a JSONL file.
 * EventEntity rows are written immediately; SyncWorker drains them when online.
 */
class EventQueue(private val dir: File) {

    private val MAX_QUEUE_SIZE = 5000
    private val queueFile = File(dir, "event_queue.jsonl")
    private val pendingCount = AtomicLong(0)
    private val memQueue = ConcurrentLinkedQueue<String>()

    init {
        dir.mkdirs()
        if (queueFile.exists()) {
            queueFile.forEachLine { if (it.isNotBlank()) memQueue.add(it) }
            pendingCount.set(memQueue.size.toLong())
        }
    }

    suspend fun enqueue(event: EventPayload) = withContext(Dispatchers.IO) {
        val json = event.toJson()
        memQueue.add(json)
        queueFile.appendText(json + "\n")
        pendingCount.incrementAndGet()

        if (memQueue.size > MAX_QUEUE_SIZE) {
            // Evict oldest temp events first; keep critical events at all costs.
            val iter = memQueue.iterator()
            var evicted = 0
            val target = memQueue.size - MAX_QUEUE_SIZE
            while (iter.hasNext() && evicted < target) {
                val entry = iter.next()
                if (entry.contains("\"type\":\"temp\"")) {
                    iter.remove()
                    evicted++
                }
            }
            pendingCount.addAndGet(-evicted.toLong())
            // Rewrite the on-disk file to match in-memory state
            queueFile.writeText(memQueue.joinToString("\n") + if (memQueue.isNotEmpty()) "\n" else "")
            android.util.Log.w("EventQueue", "Evicted $evicted old temp events to stay under cap")
        }
    }

    suspend fun drainAll(): List<EventPayload> = withContext(Dispatchers.IO) {
        val lines = memQueue.toList()
        lines.mapNotNull { runCatching { EventPayload.fromJson(it) }.getOrNull() }
    }

    suspend fun markSynced(count: Int) = withContext(Dispatchers.IO) {
        repeat(count) { memQueue.poll() }
        queueFile.writeText(memQueue.joinToString("\n") + if (memQueue.isNotEmpty()) "\n" else "")
        pendingCount.addAndGet(-count.toLong())
    }

    val size: Long get() = pendingCount.get()
}

data class EventPayload(
    val deviceId: String,
    val siteId: String,
    val station: String,
    val probeId: String?,
    val type: String,
    val value: Double,
    val ts: String,
    val payload: Map<String, String> = emptyMap(),
) {
    fun toJson(): String {
        val payloadObj = JSONObject()
        payload.forEach { (k, v) -> payloadObj.put(k, v) }
        val obj = JSONObject()
        obj.put("device_id", deviceId)
        obj.put("site_id", siteId)
        obj.put("station", station)
        obj.put("probe_id", probeId)
        obj.put("type", type)
        obj.put("value", value)
        obj.put("ts", ts)
        obj.put("payload", payloadObj)
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): EventPayload {
            val obj = JSONObject(json)
            val payloadObj = obj.optJSONObject("payload") ?: JSONObject()
            val payloadMap = mutableMapOf<String, String>()
            payloadObj.keys().forEach { k -> payloadMap[k] = payloadObj.getString(k) }
            return EventPayload(
                deviceId = obj.getString("device_id"),
                siteId = obj.getString("site_id"),
                station = obj.getString("station"),
                probeId = obj.optString("probe_id").takeIf { it != "null" && it.isNotEmpty() },
                type = obj.getString("type"),
                value = obj.getDouble("value"),
                ts = obj.getString("ts"),
                payload = payloadMap,
            )
        }
    }
}
