package com.fix.engine.abdullah.service

import android.content.Context
import com.tonyodev.fetch2.DefaultFetchNotificationManager
import com.tonyodev.fetch2.DownloadNotification
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Professional Custom Notification Manager (MB Progress)
 */
class CustomFetchNotificationManager(context: Context) : DefaultFetchNotificationManager(context) {

    /**
     * 🚀 تخصيص النص الفرعي للإشعار ليعرض (المُحمّل / الإجمالي) بالميجابايت
     */
    override fun getSubtitleText(context: Context, downloadNotification: DownloadNotification): String {
        val downloadedBytes = downloadNotification.downloaded
        val totalBytes = downloadNotification.total

        // في حال كان السيرفر لم يرسل الحجم الإجمالي بعد
        if (totalBytes <= 0L) {
            return "جاري حساب الحجم..."
        }

        // تحويل البايتات إلى ميجابايت بدقة
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)

        // استخدام Locale.US لمنع انقلاب الأرقام والشرطة المائلة في الأجهزة العربية
        return String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMB, totalMB)
    }

    /**
     * 🟢 تخصيص عنوان الإشعار ليكون أكثر احترافية ووضوحاً للمستخدم
     */
    override fun getNotificationTitle(downloadNotification: DownloadNotification): String {
        // يجلب اسم الملف (مثل whatsapp_v1.1.apk)
        val fileName = super.getNotificationTitle(downloadNotification) 
        
        return "متجر Abdullah - تنزيل التطبيق"
    }
}
