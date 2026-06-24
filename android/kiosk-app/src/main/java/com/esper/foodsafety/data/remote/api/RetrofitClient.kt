package com.esper.foodsafety.data.remote.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    @Volatile private var cachedApi: BackendApi? = null
    @Volatile private var cachedBaseUrl: String? = null

    fun create(baseUrl: String): BackendApi {
        val normalized = baseUrl.trimEnd('/') + '/'
        cachedApi?.let { if (cachedBaseUrl == normalized) return it }
        synchronized(this) {
            val existing = cachedApi
            if (existing != null && cachedBaseUrl == normalized) return existing
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(BackendApi::class.java)
            cachedApi = api
            cachedBaseUrl = normalized
            return api
        }
    }
}
