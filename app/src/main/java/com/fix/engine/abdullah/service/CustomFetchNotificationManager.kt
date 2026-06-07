package com.fix.engine.abdullah.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tonyodev.fetch2.DefaultFetchNotificationManager
import com.tonyodev.fetch2.DownloadNotification
import com.tonyodev.fetch2.Fetch
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Professional Custom Notification Manager (Android 12+ Crash Fix)
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

    // 🛡️ الحل الجذري لانهيار أندرويد 12 (API 31+):
    // بما أن المترجم يرفض قيمة Null، سنقوم بإنشاء PendingIntent وهمي وآمن جداً
    // يحتوي على علامة FLAG_IMMUTABLE الإجبارية لمنع الانهيار.
    override fun getActionPendingIntent(
        downloadNotification: DownloadNotification,
        actionType: DownloadNotification.ActionType
    ): PendingIntent {
        val dummyIntent = Intent("com.fix.engine.abdullah.DUMMY_ACTION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, downloadNotification.notificationId, dummyIntent, flags)
    }

    // 🛡️ تطبيق نفس الحماية المنيعة على إشعارات المجموعات
    override fun getGroupActionPendingIntent(
        groupId: Int,
        downloadNotifications: List<DownloadNotification>,
        actionType: DownloadNotification.ActionType
    ): PendingIntent {
        val dummyIntent = Intent("com.fix.engine.abdullah.DUMMY_GROUP_ACTION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, groupId, dummyIntent, flags)
    }
}
