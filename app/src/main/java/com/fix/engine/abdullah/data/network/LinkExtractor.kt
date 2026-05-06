package com.fix.engine.abdullah.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object LinkExtractor {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        // إذا كان الرابط ميديا فاير، نحتاج لجلب الصفحة وقراءة رابط التحميل
        // هنا نضع منطق الـ Web Scraping البسيط أو قراءة الـ Header
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString() // يعيد الرابط بعد التحويلات (Redirects)
        }
    }
}
