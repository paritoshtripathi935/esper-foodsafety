package com.esper.foodsafety.ai

import android.content.Context
import android.util.Log
import com.esper.foodsafety.voice.StructuredAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

object OnDeviceLLM {

    private const val TAG = "OnDeviceLLM"

    private val mutex = Mutex()
    private var handle: Long = 0L

    private val SAFE_DEFAULT = StructuredAction(
        actionTaken = "",
        rootCause   = "",
        disposition = "",
        severity    = "high",
    )

    private val SYSTEM_PROMPT = """
You are a food-safety JSON API. Output ONLY a single JSON object, nothing else.

Example:
Transcript: Chicken was at 165F internal, placed back in warmer.
{"action_taken":"Reheated chicken to 165F and placed in warmer","root_cause":"Temperature dropped during holding","disposition":"Product retained and placed back in service","severity":"medium"}

Transcript: Cooler alarm triggered at 48F. Checked door gasket, found tear. Replaced gasket. Temp back to 38F in 30 min. All items checked, no spoilage.
{"action_taken":"Replaced torn door gasket","root_cause":"Faulty door gasket caused temperature rise","disposition":"All items inspected, no spoilage, retained","severity":"high"}
""".trimIndent()

    suspend fun warmUp(context: Context) {
        if (!ModelDownloader.isDownloaded(context)) {
            Log.d(TAG, "Model not downloaded yet — skipping warm-up")
            return
        }
        mutex.withLock {
            if (handle == 0L) handle = initModel(context)
        }
        Log.d(TAG, "Warm-up complete, handle=$handle")
    }

    suspend fun structureNote(context: Context, transcript: String): StructuredAction =
        withContext(Dispatchers.IO) {
            try {
                val h = mutex.withLock {
                    if (handle == 0L) {
                        if (!ModelDownloader.isDownloaded(context)) {
                            Log.w(TAG, "Model not downloaded — returning safe default")
                            return@withContext SAFE_DEFAULT
                        }
                        handle = initModel(context)
                    }
                    handle
                }
                if (h == 0L) {
                    Log.e(TAG, "Model failed to load")
                    return@withContext SAFE_DEFAULT
                }

                val prompt = "<|system|>\n$SYSTEM_PROMPT\n<|user|>\nTranscript: $transcript\n<|assistant|>\n{"
                // Prompt ends with "{" so prepend it to the model's completion
                val rawText = "{" + LlamaJNI.infer(h, prompt, 256).trim()
                Log.d(TAG, "Raw response: $rawText")
                parseJson(rawText) ?: SAFE_DEFAULT
            } catch (e: Exception) {
                Log.e(TAG, "On-device inference failed", e)
                SAFE_DEFAULT
            }
        }

    fun close() {
        if (handle != 0L) {
            LlamaJNI.freeModel(handle)
            handle = 0L
        }
    }

    private fun initModel(context: Context): Long {
        val modelPath = ModelDownloader.modelFile(context).absolutePath
        Log.d(TAG, "Loading model from $modelPath")
        val h = LlamaJNI.loadModel(modelPath, nCtx = 2048, nThreads = 4)
        if (h == 0L) Log.e(TAG, "loadModel returned 0")
        return h
    }

    private fun parseJson(raw: String): StructuredAction? {
        val start = raw.indexOf('{')
        val end   = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            Log.w(TAG, "No JSON block found in response: $raw")
            return null
        }
        return try {
            val obj = JSONObject(raw.substring(start, end + 1))
            StructuredAction(
                actionTaken = obj.optString("action_taken", ""),
                rootCause   = obj.optString("root_cause",   ""),
                disposition = obj.optString("disposition",  ""),
                severity    = obj.optString("severity", "high")
                    .lowercase()
                    .let { if (it in setOf("low", "medium", "high", "critical")) it else "high" },
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: ${e.message} — raw=$raw")
            null
        }
    }
}
