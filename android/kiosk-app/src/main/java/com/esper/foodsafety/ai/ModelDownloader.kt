package com.esper.foodsafety.ai

import android.content.Context
import android.util.Log
import com.esper.foodsafety.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val TAG = "ModelDownloader"

    val modelFile: (Context) -> File = { ctx ->
        File(ctx.filesDir, BuildConfig.ON_DEVICE_MODEL_FILENAME)
    }

    fun isDownloaded(context: Context): Boolean = modelFile(context).exists()

    suspend fun ensureDownloaded(
        context: Context,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dest = modelFile(context)
        if (dest.exists()) {
            Log.d(TAG, "Model already on disk: ${dest.absolutePath}")
            return@withContext dest
        }

        Log.d(TAG, "Downloading model from ${BuildConfig.ON_DEVICE_MODEL_URL}")
        val url = URL(BuildConfig.ON_DEVICE_MODEL_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout    = 0          // 0 = no read timeout — needed for large model files
        conn.connect()

        val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        val tmp   = File(dest.parent, "${dest.name}.part")

        try {
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf     = ByteArray(256 * 1024)
                    var written = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        written += read
                        if (total > 0) onProgress(written.toFloat() / total)
                    }
                }
            }
            tmp.renameTo(dest)
            Log.d(TAG, "Model download complete: ${dest.absolutePath}")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        dest
    }
}
