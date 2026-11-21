package com.fix.engine.abdullah.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fix.engine.abdullah.MainActivity
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.repository.AppRepository
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah - Periodic Background Update Checker (WorkManager)
 * Feature: Real JSON Version & Store Tag Comparison + Smart Notification Throttling
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val UNIQUE_WORK_NAME = "abdullah_store_periodic_update_check"
        private const val PREF_LAST_NOTIFIED_SIG = "last_notified_updates_sig"

        init {
            System.loadLibrary("native-lib")
        }

        fun schedulePeriodicCheck(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }

    private external fun getSecureRepoUrl(): String

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        return try {
            val secureUrl = getSecureRepoUrl()
            val repository = AppRepository()
            val result = repository.fetchAppsDirect(secureUrl)

            val apps = result.getOrElse {
                return Result.retry()
            }

            val pm = applicationContext.packageManager
            val flags = PackageManager.GET_META_DATA
            val updatableApps = mutableListOf<String>()

            for (app in apps) {
                try {
                    val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(app.packageName, flags)
                    }

                    val serverTag = app.manifestTag
                    if (!serverTag.isNullOrBlank()) {
                        val installedTag = pInfo.applicationInfo?.metaData?.getString("ABDULLAH_STORE_TAG")
                        if (installedTag == null || installedTag.trim() != serverTag.trim()) {
                            continue
                        }
                    }

                    val installedVersion = pInfo.versionName ?: ""
                    if (compareVersionNames(app.versionName, installedVersion) > 0) {
                        updatableApps.add("${app.name} (${app.versionName})")
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                } catch (_: Exception) {
                }
            }

            if (updatableApps.isNotEmpty()) {
                val signature = updatableApps.sorted().joinToString("|")
                val prefs = applicationContext.getSharedPreferences("FixEnginePrefs", Context.MODE_PRIVATE)
                val lastNotified = prefs.getString(PREF_LAST_NOTIFIED_SIG, "")

                if (signature != lastNotified) {
                    val previewNames = updatableApps.take(3).joinToString("، ")
                    val body = if (updatableApps.size == 1) {
                        "يتوفر تحديث جديد لـ $previewNames داخل متجر Abdullah."
                    } else {
                        "تتوفر ${updatableApps.size} تحديثات جديدة: $previewNames."
                    }
                    sendNotification("تحديثات جديدة متوفرة لتطبيقاتك! 🚀", body)
                    prefs.edit().putString(PREF_LAST_NOTIFIED_SIG, signature).apply()
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun compareVersionNames(serverVersion: String, installedVersion: String): Int {
        try {
            val sParts = serverVersion.trim().split(".")
            val iParts = installedVersion.trim().split(".")
            val maxLen = maxOf(sParts.size, iParts.size)
            for (i in 0 until maxLen) {
                val sVal = sParts.getOrNull(i)?.toIntOrNull() ?: 0
                val iVal = iParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (sVal > iVal) return 1
                if (sVal < iVal) return -1
            }
        } catch (_: Exception) {
            return if (serverVersion.trim() != installedVersion.trim()) 1 else 0
        }
        return 0
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "abdullah_store_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تحديثات متجر Abdullah",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات تلقائية عند توفر تحديثات جديدة للتطبيقات"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, pendingFlags)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(applicationContext, R.color.md_theme_d_primary))
            .build()

        notificationManager.notify(1002, notification)
    }
}
