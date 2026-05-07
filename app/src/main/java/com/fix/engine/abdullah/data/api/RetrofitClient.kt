package com.fix.engine.abdullah.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Core Network Client
 * Purpose: Handling API calls with advanced configurations (Timeout & Logging)
 */
object RetrofitClient {
    
    // الرابط الأساسي للمستودع
    private const val BASE_URL = "https://raw.githubusercontent.com/"

    /**
     * إعداد عميل OkHttp لإضافة ميزات إضافية مثل وقت الانتظار والمراقبة
     */
    private val okHttpClient: OkHttpClient by lazy {
        // إضافة مراقب لمشاهدة الروابط والبيانات في Logcat أثناء التطوير
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS) // مهلة الاتصال
            .readTimeout(30, TimeUnit.SECONDS)    // مهلة القراءة
            .writeTimeout(30, TimeUnit.SECONDS)   // مهلة الكتابة
            .retryOnConnectionFailure(true)      // إعادة المحاولة عند فشل الاتصال
            .build()
    }

    /**
     * بناء كائن Retrofit
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // ربط Retrofit بالعميل الذي أعددناه
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * تعريف الخدمة التي سنستخدمها لجلب البيانات
     */
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
