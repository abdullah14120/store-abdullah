package com.fix.engine.abdullah.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Core Network Client
 * Update: Added 'instance' reference to fix Build Errors
 */
object RetrofitClient {
    
    // الرابط الأساسي لمستودعات GitHub الخام
    private const val BASE_URL = "https://raw.githubusercontent.com/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // نستخدم Level.BASIC في الإنتاج و BODY في التطوير
            level = HttpLoggingInterceptor.Level.BODY 
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS) // زيادة المهلة لتناسب الإنترنت الضعيف
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
     * هذا هو المتغير الذي يبحث عنه الـ Repository والـ ViewModel
     * قمت بإضافة "instance" كاسم مستعار لـ apiService لضمان التوافق
     */
    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // للمحافظة على التوافق مع أي كود قديم يستخدم apiService مباشرة
    val apiService: ApiService get() = instance
}
