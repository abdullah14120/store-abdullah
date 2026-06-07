package com.fix.engine.abdullah.service

import android.app.PendingIntent
import android.content.Context
import com.tonyodev.fetch2.DefaultFetchNotificationManager
import com.tonyodev.fetch2.DownloadNotification
import com.tonyodev.fetch2.Fetch
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Professional Custom Notification Manager (Android 12+ Crash Fix)
 */
class CustomFetchNotificationManager(context: Context) : DefaultFetchNotificationManager(context) {

    // 🚀 ربط الإشعارات بمحرك Fetch الافتراضي
    override fun getFetchInstanceForNamespace(namespace: String): Fetch {
        return Fetch.getDefaultInstance()
    }

    // 🚀 تخصيص النص الفرعي للإشعار ليعرض (المُحمّل / الإجمالي) بالميجابايت
    override fun getSubtitleText(context: Context, downloadNotification: DownloadNotification): String {
        val downloadedBytes = downloadNotification.downloaded
        val totalBytes = downloadNotification.total

        if (totalBytes <= 0L) {
            return "جاري حساب الحجم..."
        }

        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)

        return String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMB, totalMB)
    }

    // 🛡️ الحل السحري لانهيار أندرويد 12 (API 31+):
    // نمنع المكتبة من إنشاء الأزرار القديمة (إيقاف/إلغاء) داخل الإشعار والتي تسبب الانهيار.
    // سيكتفي الإشعار بعرض التقدم بشكل أنيق، بينما يتحكم المستخدم بالتحميل من واجهة المتجر.
    override fun getActionPendingIntent(
        downloadNotification: DownloadNotification,
        actionType: DownloadNotification.ActionType
    ): PendingIntent? {
        return null // إرجاع Null يمنع رسم الأزرار ويتجاوز الخطأ الأمني
    }

    // 🛡️ تطبيق نفس الحماية على إشعارات المجموعات
    override fun getGroupActionPendingIntent(
        groupId: Int,
        downloadNotifications: List<DownloadNotification>,
        actionType: DownloadNotification.ActionType
    ): PendingIntent? {
        return null
    }
}
