package com.fix.engine.abdullah.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Advanced Link Engine
 * Feature: Ultra-Fast HEAD Requests & MediaFire Direct Link Scraper
 */
object LinkExtractor {

    // إعداد عميل بخيارات صارمة للسرعة وعدم إهدار باقة الإنترنت
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        try {
            val urlLower = url.lowercase()

            // 1. معالجة روابط MediaFire بشكل خاص (Scraping)
            if (urlLower.contains("mediafire.com")) {
                return@withContext extractMediaFireLink(url)
            }

            // 2. معالجة الروابط العادية باستخدام طلب HEAD السريع جداً (لا يحمل محتوى الملف)
            val request = Request.Builder()
                .url(url)
                .head() // 🚀 تعديل جوهري: يجلب الـ Headers فقط وتتبع التحويل بدون تنزيل الـ APK في الذاكرة
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.isRedirect) {
                    response.request.url.toString()
                } else {
                    url 
                }
            }
        } catch (e: Exception) {
            url 
        }
    }

    /**
     * 🟢 محرك كشط روابط ميديا فاير: يقرأ الـ HTML ويستخرج الرابط المباشر للـ APK
     */
    private fun extractMediaFireLink(mediaFireUrl: String): String {
        try {
            val request = Request.Builder()
                .url(mediaFireUrl)
                .get() // هنا نحتاج GET لقراءة صفحة الـ HTML
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val htmlBody = response.body?.string() ?: return mediaFireUrl
                    
                    // استخدام Regex للبحث عن زر التحميل الخاص بميديا فاير
                    // الرابط المباشر يكون دائماً داخل href ويبدأ بـ http://download...
                    val pattern = Pattern.compile("href=\"(https?://[a-zA-Z0-9-]+\\.mediafire\\.com/download/[^\"]+)\"")
                    val matcher = pattern.matcher(htmlBody)
                    
                    if (matcher.find()) {
                        return matcher.group(1) ?: mediaFireUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mediaFireUrl // العودة للرابط الأصلي في حال فشل الكشط
    }
}
