package com.fix.engine.abdullah

import android.app.Application
import com.fix.engine.abdullah.service.CustomFetchNotificationManager
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2core.Downloader
import com.tonyodev.fetch2okhttp.OkHttpUrlConnectionDownloader // 🚀 تأكد من إضافة مكتبة دعم OkHttp لـ Fetch إذا لم تكن موجودة
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Enterprise Edition
 * Feature: Ultra-Fast Parallel Downloading, Auto-Retry & Custom Notification Engine
 */
class AbdullahStoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. تهيئة مدير الإشعارات الاحترافي
        val notificationManager = CustomFetchNotificationManager(this)

        // ⚡ 2. إعداد عميل OkHttp مخصص للتعامل مع الشبكات الضعيفة (Yemen Networks)
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS) // زيادة وقت انتظار الاتصال
            .readTimeout(20, TimeUnit.SECONDS)    // زيادة وقت قراءة البيانات
            .build()

        // 🚀 3. إعداد محرك Fetch بأقصى طاقة ممكنة متوافقة مع البنية التحتية
        val fetchConfiguration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(2) 
            
            // 🔥 الترقية إلى OkHttpDownloader للتعامل المثالي مع Cloudflare والشبكات المتقطعة
            .setHttpDownloader(OkHttpUrlConnectionDownloader(okHttpClient, Downloader.FileDownloaderType.PARALLEL))
            
            .setAutoRetryMaxAttempts(3)
            .setNamespace("AbdullahStoreDownloads") 
            .setNotificationManager(notificationManager) 
            .build()

        Fetch.setDefaultInstanceConfiguration(fetchConfiguration)

        // 🧹 4. تنظيف حالة التحميلات عند إقلاع التطبيق (إيقاف مؤقت لأي تحميل معلق من جلسة سابقة)
        // هذا يمنع استنزاف باقة الإنترنت للمستخدم بشكل مفاجئ عند فتح التطبيق
        val fetch = Fetch.getDefaultInstance()
        fetch.pauseAll()
    }
}
