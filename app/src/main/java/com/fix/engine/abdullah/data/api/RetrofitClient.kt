package com.fix.engine.abdullah.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.fix.engine.abdullah.BuildConfig 

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: High Performance & Secure Network Client
 * Feature: Optimized for slow connections & Dynamic API Calls with Production Security
 */
object RetrofitClient {
    
    // الرابط الأساسي، ويتم تجاوزه (Override) بفضل @Url في ApiService
    private const val BASE_URL = "https://raw.githubusercontent.com/"

    private val okHttpClient: OkHttpClient by lazy {
        // 🔒 حماية صارمة: تشغيل مراقب البيانات فقط أثناء البرمجة (Debug) وإيقافه كلياً للمستخدمين (Release)
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            // إعدادات تناسب كافة سرعات الإنترنت لضمان عدم توقف المتجر
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) 
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
     * يتم استخدامه في AppRepository لجلب البيانات بكفاءة وبدون استهلاك للذاكرة
     */
    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // دعم التسمية القديمة لضمان التوافقية وعدم حدوث خطأ في الملفات الأخرى
    val apiService: ApiService get() = instance
}
