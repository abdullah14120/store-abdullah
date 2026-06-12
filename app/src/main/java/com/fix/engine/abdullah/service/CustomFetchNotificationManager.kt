package com.fix.engine.abdullah.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tonyodev.fetch2.DefaultFetchNotificationManager
import com.tonyodev.fetch2.DownloadNotification
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchNotificationManager
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Professional Custom Notification Manager (Android 12+ Crash Fix & Active Actions)
 */
class CustomFetchNotificationManager(private val context: Context) : DefaultFetchNotificationManager(context) {

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

    // 🛡️ الحل الاحترافي لانهيار أندرويد 12+: بناء الـ Intent الحقيقي لدعم عمل أزرار (إلغاء/إيقاف)
    override fun getActionPendingIntent(
        downloadNotification: DownloadNotification,
        actionType: DownloadNotification.ActionType
    ): PendingIntent? {
        // بناء الـ Intent الذي تفهمه مكتبة Fetch تماماً
        val intent = Intent(FetchNotificationManager.ACTION_NOTIFICATION_ACTION).apply {
            putExtra(FetchNotificationManager.EXTRA_NAMESPACE, downloadNotification.namespace)
            putExtra(FetchNotificationManager.EXTRA_DOWNLOAD_ID, downloadNotification.notificationId)
            putExtra(FetchNotificationManager.EXTRA_NOTIFICATION_ID, downloadNotification.notificationId)
            putExtra(FetchNotificationManager.EXTRA_GROUP_ACTION, false)
            putExtra(FetchNotificationManager.EXTRA_ACTION_TYPE, actionType.value)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // استخدام requestCode فريد لكل زر لكي لا تتداخل أوامر الإلغاء مع الإيقاف المؤقت
        val requestCode = downloadNotification.notificationId + actionType.value
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    // 🛡️ تطبيق نفس الحماية والوظائف على إشعارات المجموعات (Group Notifications)
    override fun getGroupActionPendingIntent(
        groupId: Int,
        downloadNotifications: List<DownloadNotification>,
        actionType: DownloadNotification.ActionType
    ): PendingIntent? {
        val intent = Intent(FetchNotificationManager.ACTION_NOTIFICATION_ACTION).apply {
            putExtra(FetchNotificationManager.EXTRA_NAMESPACE, downloadNotifications.firstOrNull()?.namespace ?: Fetch.getDefaultInstance().namespace)
            putExtra(FetchNotificationManager.EXTRA_DOWNLOAD_ID, groupId)
            putExtra(FetchNotificationManager.EXTRA_NOTIFICATION_ID, groupId)
            putExtra(FetchNotificationManager.EXTRA_GROUP_ACTION, true)
            putExtra(FetchNotificationManager.EXTRA_ACTION_TYPE, actionType.value)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val requestCode = groupId + actionType.value
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
