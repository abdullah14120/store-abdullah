package com.fix.engine.abdullah.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fix.engine.abdullah.R // تأكد من استيراد موارد تطبيقك
import com.tonyodev.fetch2.DefaultFetchNotificationManager
import com.tonyodev.fetch2.DownloadNotification
import com.tonyodev.fetch2.Fetch
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Professional Custom Notification Manager (Android 12+ Crash Fix, Speed Info & UI Polishing)
 */
class CustomFetchNotificationManager(private val context: Context) : DefaultFetchNotificationManager(context) {

    // 🚀 ربط الإشعارات بمحرك Fetch الافتراضي
    override fun getFetchInstanceForNamespace(namespace: String): Fetch {
        return Fetch.getDefaultInstance()
    }

    // 🎨 الحل الاحترافي لمشكلة "المربع الأبيض" في الإشعارات
    override fun getNotificationBuilder(
        notificationId: Int,
        groupId: Int
    ): NotificationCompat.Builder {
        val builder = super.getNotificationBuilder(notificationId, groupId)
        // استخدام أيقونة شفافة مخصصة لشريط الحالة بدلاً من أيقونة التطبيق الملونة
        builder.setSmallIcon(R.drawable.ic_notification_transparent)
        // تخصيص لون الأيقونة لتتماشى مع هوية المتجر (مثلاً اللون الأساسي)
        builder.color = context.getColor(R.color.md_theme_d_primary) 
        return builder
    }

    // 🚀 تخصيص النص الفرعي ليعرض الحجم المتبقي بالإضافة إلى سرعة التحميل اللحظية
    override fun getSubtitleText(context: Context, downloadNotification: DownloadNotification): String {
        val downloadedBytes = downloadNotification.downloaded
        val totalBytes = downloadNotification.total
        
        // Fetch2 يوفر سرعة التحميل بالبايت في الثانية، سنقوم بتحويلها لعرض احترافي
        val downloadedBytesPerSecond = downloadNotification.downloadedBytesPerSecond

        if (totalBytes <= 0L) {
            return "جاري حساب الحجم..."
        }

        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)
        
        // حساب سرعة التحميل بصيغة (KB/s أو MB/s)
        val speedKB = downloadedBytesPerSecond / 1024.0
        val speedText = if (speedKB >= 1024.0) {
            val speedMB = speedKB / 1024.0
            String.format(Locale.US, "%.1f MB/s", speedMB)
        } else {
            String.format(Locale.US, "%.1f KB/s", speedKB)
        }

        // النتيجة: "2.4 MB/s • 15.5 MB / 50.0 MB"
        return String.format(Locale.US, "%s • %.1f MB / %.1f MB", speedText, downloadedMB, totalMB)
    }

    // 🛡️ الحل الاحترافي لانهيار أندرويد 12+
    override fun getActionPendingIntent(
        downloadNotification: DownloadNotification,
        actionType: DownloadNotification.ActionType
    ): PendingIntent {
        
        val intent = Intent("com.tonyodev.fetch2.action.NOTIFICATION_ACTION").apply {
            putExtra("com.tonyodev.fetch2.extra.NAMESPACE", downloadNotification.namespace)
            putExtra("com.tonyodev.fetch2.extra.DOWNLOAD_ID", downloadNotification.notificationId)
            putExtra("com.tonyodev.fetch2.extra.NOTIFICATION_ID", downloadNotification.notificationId)
            putExtra("com.tonyodev.fetch2.extra.GROUP_ACTION", false)
            putExtra("com.tonyodev.fetch2.extra.ACTION_TYPE", actionType.name)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val requestCode = downloadNotification.notificationId + actionType.hashCode()
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)!!
    }

    // 🛡️ تطبيق الحماية على إشعارات المجموعات
    override fun getGroupActionPendingIntent(
        groupId: Int,
        downloadNotifications: List<DownloadNotification>,
        actionType: DownloadNotification.ActionType
    ): PendingIntent {
        
        val intent = Intent("com.tonyodev.fetch2.action.NOTIFICATION_ACTION").apply {
            putExtra("com.tonyodev.fetch2.extra.NAMESPACE", downloadNotifications.firstOrNull()?.namespace ?: Fetch.getDefaultInstance().namespace)
            putExtra("com.tonyodev.fetch2.extra.DOWNLOAD_ID", groupId)
            putExtra("com.tonyodev.fetch2.extra.NOTIFICATION_ID", groupId)
            putExtra("com.tonyodev.fetch2.extra.GROUP_ACTION", true)
            putExtra("com.tonyodev.fetch2.extra.ACTION_TYPE", actionType.name)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val requestCode = groupId + actionType.hashCode()
return PendingIntent.getBroadcast(context, requestCode, intent, flags)!!
    }
}
