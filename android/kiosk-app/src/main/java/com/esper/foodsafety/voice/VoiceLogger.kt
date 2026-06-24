package com.esper.foodsafety.voice

import android.content.Context
import android.util.Log
import com.esper.foodsafety.BuildConfig
import com.esper.foodsafety.ai.OnDeviceLLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class StructuredAction(
    val actionTaken: String,
    val rootCause: String,
    val disposition: String,
    val severity: String,
)

object VoiceLogger {

    private const val TAG = "VoiceLogger"

    /**
     * Structure a voice transcript into corrective-action fields.
     *
     * Strategy:
     *  1. Try on-device LLM (offline-capable, instant).
     *  2. If on-device returns an empty result or throws, fall back to EC2 /ai/structure-note.
     *  3. If both fail, return a safe default with severity=high so the record is never lost.
     */
    suspend fun structureNote(
        context: Context,
        transcript: String,
    ): Result<StructuredAction> {
        // 1 — on-device
        val onDevice = runCatching { OnDeviceLLM.structureNote(context, transcript) }
        val candidate = onDevice.getOrNull()
        if (candidate != null && candidate.actionTaken.isNotBlank()) {
            Log.d(TAG, "structureNote: used on-device model")
            return Result.success(candidate)
        }

        if (onDevice.isFailure) {
            Log.w(TAG, "On-device inference failed", onDevice.exceptionOrNull())
        } else {
            Log.d(TAG, "On-device returned empty fields — trying EC2 fallback")
        }

        // 2 — EC2 fallback
        return structureNoteRemote(transcript)
    }

    private suspend fun structureNoteRemote(transcript: String): Result<StructuredAction> =
        withContext(Dispatchers.IO) {
            try {
                val aiBase = BuildConfig.AI_API_BASE.trimEnd('/')
                val url    = URL("$aiBase/ai/structure-note")
                val conn   = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput     = true
                conn.connectTimeout = 12_000
                conn.readTimeout    = 12_000

                val bodyObj = org.json.JSONObject()
                bodyObj.put("transcript", transcript)
                val body = bodyObj.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val obj  = JSONObject(text)
                    Log.d(TAG, "structureNote: used EC2")
                    Result.success(
                        StructuredAction(
                            actionTaken = obj.optString("action_taken", ""),
                            rootCause   = obj.optString("root_cause",   ""),
                            disposition = obj.optString("disposition",  ""),
                            severity    = obj.optString("severity",     "medium"),
                        )
                    )
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
                    Log.e(TAG, "EC2 structure-note error: $err")
                    // 3 — safe default so the corrective action is never silently dropped
                    Result.success(StructuredAction("", "", "", "high"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "structureNoteRemote failed", e)
                // 3 — safe default
                Result.success(StructuredAction("", "", "", "high"))
            }
        }
}
