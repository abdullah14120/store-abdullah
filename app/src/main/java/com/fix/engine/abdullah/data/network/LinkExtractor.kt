package com.fix.engine.abdullah.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Advanced Link Engine
 * Feature: Smart Redirect Following & MediaFire Support
 */
object LinkExtractor {

    // إعداد عميل خاص بالروابط مع مهلة زمنية ذكية
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * وظيفة استخراج الرابط النهائي بعد كافة التحويلات.
     * مفيدة جداً لروابط MediaFire و Google Drive و Dropbox.
     */
    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/113.0 Firefox/113.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // يعيد الرابط النهائي بعد تتبع كافة الـ Redirects
                    response.request.url.toString()
                } else {
                    url // في حال الفشل نعود للرابط الأصلي
                }
            }
        } catch (e: Exception) {
            url // في حال حدوث خطأ شبكة، نستخدم الرابط الخام كما هو
        }
    }
}
