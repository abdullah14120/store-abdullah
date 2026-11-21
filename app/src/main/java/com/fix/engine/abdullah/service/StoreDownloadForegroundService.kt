package com.fix.engine.abdullah.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fix.engine.abdullah.MainActivity
import com.fix.engine.abdullah.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Inspired by: SealPlus & Komi Store DownloadForegroundService
 * Feature: Android 12+ Background FGS Exception Shield, Android 15 onTimeout Guard,
 *          Throttled Progress Notification (700ms) & Direct Cancel Action
 */
class StoreDownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var lastNotifyMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 🛡️ حماية أندرويد 15 (API 35+): إيقاف الحارس بأمان عند انتهاء مهلة dataSync دون قتل التطبيق
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForegroundSafely()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            val cancelId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
            if (cancelId != -1) {
                StoreDownloadEngine.cancel(cancelId, deletePartial = true)
            }
            if (!StoreDownloadEngine.hasActiveDownloads()) {
                stopForegroundSafely()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        ensureChannel(this)

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        // 🛡️ حماية أندرويد 12+ ضد ForegroundServiceStartNotAllowedException عند الاستئناف في الخلفية
        val started = runCatching {
            val initialTask = currentActiveTasks().firstOrNull()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(initialTask, 1), fgsType)
        }.isSuccess

        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                StoreDownloadEngine.downloadsState.collect { map ->
                    val active = map.values.filter {
                        it.status == StoreDownloadStatus.QUEUED ||
                            it.status == StoreDownloadStatus.RESOLVING ||
                            it.status == StoreDownloadStatus.DOWNLOADING ||
                            it.status == StoreDownloadStatus.PAUSED_NETWORK
                    }

                    if (active.isEmpty()) {
                        stopForegroundSafely()
                        stopSelf()
                        return@collect
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotifyMs >= NOTIFICATION_THROTTLE_MS || active.any { it.status == StoreDownloadStatus.PAUSED_NETWORK }) {
                        lastNotifyMs = now
                        runCatching {
                            NotificationManagerCompat.from(this@StoreDownloadForegroundService)
                                .notify(NOTIFICATION_ID, buildNotification(active.first(), active.size))
                        }
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun currentActiveTasks(): List<StoreDownloadTask> {
        return StoreDownloadEngine.downloadsState.value.values.filter {
            it.status == StoreDownloadStatus.QUEUED ||
                it.status == StoreDownloadStatus.RESOLVING ||
                it.status == StoreDownloadStatus.DOWNLOADING ||
                it.status == StoreDownloadStatus.PAUSED_NETWORK
        }
    }

    private fun buildNotification(task: StoreDownloadTask?, activeCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)

        val title = when {
            task == null -> "جاري التحميل..."
            activeCount > 1 -> "جاري تنزيل $activeCount تطبيقات..."
            task.status == StoreDownloadStatus.PAUSED_NETWORK -> "بانتظار الشبكة: ${task.displayName}"
            else -> "جاري تنزيل ${task.displayName}"
        }

        val subtitle = task?.statusMessage ?: "متجر Abdullah يواصل التنزيل في الخلفية..."
        val progress = task?.progress?.coerceIn(0, 100) ?: 0
        val indeterminate = task == null ||
            task.status == StoreDownloadStatus.RESOLVING ||
            task.status == StoreDownloadStatus.QUEUED ||
            (progress == 0 && task.status == StoreDownloadStatus.DOWNLOADING)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setColor(ContextCompat.getColor(this, R.color.md_theme_d_primary))
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setProgress(100, progress, indeterminate)

        if (task != null) {
            val cancelIntent = Intent(this, StoreDownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, task.id)
            }
            val cancelPending = PendingIntent.getService(this, task.id, cancelIntent, pendingFlags)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancelPending)
        }

        return builder.build()
    }

    private fun stopForegroundSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        stopForegroundSafely()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "abdullah_store_downloads_v2"
        const val NOTIFICATION_ID = 7401
        private const val ACTION_CANCEL_DOWNLOAD = "com.fix.engine.abdullah.ACTION_CANCEL_DOWNLOAD"
        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val NOTIFICATION_THROTTLE_MS = 700L

        fun ensureChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName("تنزيلات متجر Abdullah")
                .setDescription("عرض تقدم تنزيل التطبيقات في الخلفية")
                .setShowBadge(false)
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        fun sync(context: Context) {
            ensureChannel(context)
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, StoreDownloadForegroundService::class.java)
                )
            }
        }
    }
}
