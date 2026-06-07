package com.fix.engine.abdullah

import android.app.Application
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.HttpUrlConnectionDownloader
import com.tonyodev.fetch2core.Downloader
import com.fix.engine.abdullah.service.CustomFetchNotificationManager // تأكد من صحة مسار الحزمة بناءً على مشروعك

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Enterprise Edition
 * Feature: Ultra-Fast Parallel Downloading & Custom Notification Engine
 */
class AbdullahStoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 🚀 1. تهيئة مدير الإشعارات الاحترافي (لعرض الميجابايت وحماية التطبيق من الإغلاق في الخلفية)
        val notificationManager = CustomFetchNotificationManager(this)

        // ⚡ 2. إعداد محرك Fetch بأقصى طاقة ممكنة متوافقة مع البنية التحتية المتطورة (Cloudflare)
        val fetchConfiguration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(2) // السماح بتحميل تطبيقين كحد أقصى في نفس الوقت لمنع اختناق شبكة المستخدم
            
            // 🔥 نظام التقطيع: استخدام PARALLEL لفتح اتصالات متعددة وسحب الملف بسرعة خيالية
            .setHttpDownloader(HttpUrlConnectionDownloader(Downloader.FileDownloaderType.PARALLEL))
            
            .setNamespace("AbdullahStoreDownloads") // عزل تحميلات المتجر تماماً عن أي تطبيق آخر في الجهاز
            .setNotificationManager(notificationManager) // ربط الإشعارات
            .build()

        // 3. تطبيق هذه الإعدادات لتصبح الإعدادات الافتراضية لكامل المتجر
        Fetch.setDefaultInstanceConfiguration(fetchConfiguration)
    }
}
