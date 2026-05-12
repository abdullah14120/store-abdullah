package com.fix.engine.abdullah.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - High Performance Network Client
 * Feature: Optimized for slow connections & Dynamic API Calls
 */
object RetrofitClient {
    
    private const val BASE_URL = "https://raw.githubusercontent.com/"

    private val okHttpClient: OkHttpClient by lazy {
        // مراقب البيانات لمتابعة الطلبات في الـ Logcat
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY 
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            // إعدادات تناسب كافة سرعات الإنترنت لضمان عدم توقف المتجر
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // إعادة المحاولة تلقائياً عند الفشل
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * الوصول الموحد للخدمة (Singleton Instance)
     * يتم استخدامه في AppRepository لجلب البيانات
     */
    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // دعم التسمية القديمة لضمان عدم حدوث خطأ في الملفات الأخرى
    val apiService: ApiService get() = instance
}
