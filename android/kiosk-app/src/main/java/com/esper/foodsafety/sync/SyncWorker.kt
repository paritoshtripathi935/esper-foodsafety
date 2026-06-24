package com.esper.foodsafety.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.esper.foodsafety.BuildConfig
import com.esper.foodsafety.data.remote.api.EventDto
import com.esper.foodsafety.data.remote.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queue = EventQueue(File(applicationContext.filesDir, "events"))
        val events = queue.drainAll()
        if (events.isEmpty()) return@withContext Result.success()

        return@withContext try {
            val api = RetrofitClient.create(BuildConfig.SUPABASE_URL)
            val auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
            val apiKey = BuildConfig.SUPABASE_ANON_KEY

            // POST in ts order as a single batch
            val sorted = events.sortedBy { it.ts }
            val response = api.insertEvents(
                apiKey = apiKey,
                auth = auth,
                events = sorted.map { event ->
                    EventDto(
                        device_id = event.deviceId,
                        site_id   = event.siteId,
                        station   = event.station,
                        probe_id  = event.probeId,
                        type      = event.type,
                        value     = event.value,
                        ts        = event.ts,
                        payload   = event.payload,
                    )
                }
            )

            if (!response.isSuccessful) {
                Log.e("SyncWorker", "Insert failed: HTTP ${response.code()} ${response.errorBody()?.string()}")
                return@withContext if (response.code() in 500..599 || response.code() == 429) {
                    Result.retry()
                } else {
                    Log.e("SyncWorker", "Permanent failure HTTP ${response.code()} — dropping batch")
                    queue.markSynced(sorted.size)
                    Result.failure()
                }
            }

            queue.markSynced(sorted.size)
            SyncHealth.recordSuccess(sorted.size, queue.size)
            Log.d("SyncWorker", "Synced ${sorted.size} events")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed: ${e.message}")
            SyncHealth.recordFailure(e.message ?: "unknown", queue.size)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "event_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
