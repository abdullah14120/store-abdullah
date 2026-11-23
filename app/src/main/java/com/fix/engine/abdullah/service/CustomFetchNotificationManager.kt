package com.fix.engine.abdullah.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fix.engine.abdullah.AbdullahStoreApp
import com.fix.engine.abdullah.R
import com.tonyodev.fetch2.DefaultFetchNotificationManager
import com.tonyodev.fetch2.DownloadNotification
import com.tonyodev.fetch2.Fetch
import java.util.Locale

class CustomFetchNotificationManager(context: Context) : DefaultFetchNotificationManager(context) {

    private val safeContext: Context
        get() = AbdullahStoreApp.appInstance ?: throw IllegalStateException("App instance not ready")

    override fun registerBroadcastReceiver() {
        try {
            val ctx = AbdullahStoreApp.appInstance ?: return
            ContextCompat.registerReceiver(
                ctx,
                broadcastReceiver,
                IntentFilter(notificationManagerAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            AbdullahStoreApp.appInstance?.let {
                CrashHandler.recordException(it, t, "CustomFetchNotificationManager.registerBroadcastReceiver")
            }
        }
    }

    override fun getFetchInstanceForNamespace(namespace: String): Fetch {
        return Fetch.getDefaultInstance()
    }

    override fun getNotificationBuilder(
        notificationId: Int,
        groupId: Int
    ): NotificationCompat.Builder {
        val builder = super.getNotificationBuilder(notificationId, groupId)
        builder.setSmallIcon(R.drawable.ic_notification_transparent)
        AbdullahStoreApp.appInstance?.let { ctx ->
            builder.color = ContextCompat.getColor(ctx, R.color.md_theme_d_primary)
        }
        return builder
    }

    override fun getSubtitleText(context: Context, downloadNotification: DownloadNotification): String {
        return NotificationTextFormatter.formatSubtitle(
            downloadNotification.downloaded,
            downloadNotification.total,
            downloadNotification.downloadedBytesPerSecond
        )
    }

    override fun getActionPendingIntent(
        downloadNotification: DownloadNotification,
        actionType: DownloadNotification.ActionType
    ): PendingIntent {
        val ctx = safeContext
        val intent = PendingIntentFactory.createNotificationActionIntent(
            ctx,
            downloadNotification.namespace,
            downloadNotification.notificationId,
            actionType.name,
            isGroupAction = false
        )
        val requestCode = downloadNotification.notificationId + actionType.hashCode()
        return PendingIntentFactory.getBroadcast(ctx, requestCode, intent)
    }

    override fun getGroupActionPendingIntent(
        groupId: Int,
        downloadNotifications: List<DownloadNotification>,
        actionType: DownloadNotification.ActionType
    ): PendingIntent {
        val ctx = safeContext
        val namespace = downloadNotifications.firstOrNull()?.namespace
            ?: Fetch.getDefaultInstance().namespace

        val intent = PendingIntentFactory.createNotificationActionIntent(
            ctx,
            namespace,
            groupId,
            actionType.name,
            isGroupAction = true
        )
        val requestCode = groupId + actionType.hashCode()
        return PendingIntentFactory.getBroadcast(ctx, requestCode, intent)
    }
}

object NotificationTextFormatter {

    fun formatSubtitle(downloadedBytes: Long, totalBytes: Long, downloadedBytesPerSecond: Long): String {
        if (totalBytes <= 0L) {
            return "جاري حساب الحجم..."
        }

        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)

        val speedKB = downloadedBytesPerSecond / 1024.0
        val speedText = if (speedKB >= 1024.0) {
            val speedMB = speedKB / 1024.0
            String.format(Locale.US, "%.1f MB/s", speedMB)
        } else {
            String.format(Locale.US, "%.1f KB/s", speedKB)
        }

        return String.format(Locale.US, "%s • %.1f MB / %.1f MB", speedText, downloadedMB, totalMB)
    }
}

object PendingIntentFactory {

    private const val FETCH_ACTION = "com.tonyodev.fetch2.action.NOTIFICATION_ACTION"

    fun createNotificationActionIntent(
        context: Context,
        namespace: String,
        notificationId: Int,
        actionTypeName: String,
        isGroupAction: Boolean
    ): Intent {
        return Intent(FETCH_ACTION).apply {
            setPackage(context.packageName)
            putExtra("com.tonyodev.fetch2.extra.NAMESPACE", namespace)
            putExtra("com.tonyodev.fetch2.extra.DOWNLOAD_ID", notificationId)
            putExtra("com.tonyodev.fetch2.extra.NOTIFICATION_ID", notificationId)
            putExtra("com.tonyodev.fetch2.extra.GROUP_ACTION", isGroupAction)
            putExtra("com.tonyodev.fetch2.extra.ACTION_TYPE", actionTypeName)
        }
    }

    fun getBroadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
