package com.fix.engine.abdullah.data.api

import android.content.Context
import com.fix.engine.abdullah.BuildConfig
import com.fix.engine.abdullah.data.network.FallbackDns
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: High Performance & DoH-Resilient Network Client with HTTP Disk Cache
 */
object RetrofitClient {

    private const val BASE_URL = "https://raw.githubusercontent.com/"
    private const val HTTP_CACHE_SIZE_BYTES = 32L * 1024L * 1024L

    @Volatile
    private var cacheDirRef: File? = null

    fun init(context: Context) {
        if (cacheDirRef == null) {
            cacheDirRef = File(context.applicationContext.cacheDir, "http_catalog_cache")
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val builder = OkHttpClient.Builder()
            .dns(FallbackDns)
            .addInterceptor(logging)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        cacheDirRef?.let { dir ->
            runCatching {
                if (!dir.exists()) dir.mkdirs()
                builder.cache(Cache(dir, HTTP_CACHE_SIZE_BYTES))
            }
        }

        builder.build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    val apiService: ApiService get() = instance
}
