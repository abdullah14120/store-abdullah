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
 * Feature: Ultra-Fast HEAD Requests, Google Drive Direct Link Converter & MediaFire Scraper
 */
object LinkExtractor {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        try {
            val urlLower = url.lowercase()

            // 1. معالجة روابط معاينة Google Drive وتحويلها إلى روابط تحميل مباشر
            if (urlLower.contains("drive.google.com")) {
                val fileId = extractGoogleDriveFileId(url)
                if (fileId != null) {
                    return@withContext "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
                }
            }

            // 2. معالجة روابط MediaFire (كشط الـ HTML)
            if (urlLower.contains("mediafire.com")) {
                return@withContext extractMediaFireLink(url)
            }

            // 3. الروابط العادية أو روابط GitHub باستخدام طلب HEAD السريع
            val request = Request.Builder()
                .url(url)
                .head()
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
     * استخراج معرف الملف (File ID) من روابط Google Drive بشتى أنواعها
     */
    private fun extractGoogleDriveFileId(url: String): String? {
        val pattern1 = Pattern.compile("/d/([a-zA-Z0-9_-]+)")
        val matcher1 = pattern1.matcher(url)
        if (matcher1.find()) {
            return matcher1.group(1)
        }

        val pattern2 = Pattern.compile("id=([a-zA-Z0-9_-]+)")
        val matcher2 = pattern2.matcher(url)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    /**
     * كشط روابط ميديا فاير واستخراج رابط الـ APK المباشر
     */
    private fun extractMediaFireLink(mediaFireUrl: String): String {
        try {
            val request = Request.Builder()
                .url(mediaFireUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val htmlBody = response.body?.string() ?: return mediaFireUrl
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
        return mediaFireUrl
    }
}
