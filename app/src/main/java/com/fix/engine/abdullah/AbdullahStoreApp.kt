package com.fix.engine.abdullah

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import com.fix.engine.abdullah.service.CrashHandler
import com.fix.engine.abdullah.service.CustomFetchNotificationManager
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2core.Downloader
import com.tonyodev.fetch2okhttp.OkHttpDownloader
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Android 14/15 (API 34/35) Receiver Interceptor & Crash-Protected Core
 */
class AbdullahStoreApp : Application() {

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 🛡️ ضمان إرجاع هذا الكلاس دائماً كـ ApplicationContext ليمر عبر دوال الحماية بالأسفل
     */
    override fun getApplicationContext(): Context {
        return this
    }

    /**
     * 🛡️ الحل الجذري لمشكلة PriorityListProcessorImpl داخل مكتبة Fetch2 على Android 14 و 15:
     * أي استدعاء داخلي لدالة registerReceiver القديمة بدون Flags يتم تزويده تلقائياً بـ RECEIVER_NOT_EXPORTED
     */
    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(
                receiver,
                filter,
                broadcastPermission,
                scheduler,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }

    override fun onCreate() {
        super.onCreate()

        appInstance = this
        CrashHandler.init(this)

        try {
            initFetchEngine()
        } catch (t: Throwable) {
            CrashHandler.recordException(this, t, "AbdullahStoreApp.initFetchEngine")
        }
    }

    private fun initFetchEngine() {
        val notificationManager = CustomFetchNotificationManager(this)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val fetchConfiguration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(2)
            .setHttpDownloader(OkHttpDownloader(okHttpClient, Downloader.FileDownloaderType.SEQUENTIAL))
            .setAutoRetryMaxAttempts(0)
            .enableAutoStart(true)
            .setNamespace("AbdullahStoreDownloads")
            .setNotificationManager(notificationManager)
            .build()

        Fetch.setDefaultInstanceConfiguration(fetchConfiguration)

        val fetch = Fetch.getDefaultInstance()

        fetch.addListener(object : AbstractFetchListener() {
            override fun onStarted(
                download: Download,
                downloadBlocks: List<com.tonyodev.fetch2core.DownloadBlock>,
                totalBlocks: Int
            ) {
                acquireWakeLock()
            }

            override fun onCompleted(download: Download) {
                releaseWakeLockIfIdle()
                notifyStateChanged(download.file, isCompleted = true)
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                File(download.file).delete()
                fetch.remove(download.id)
                releaseWakeLockIfIdle()
                notifyStateChanged(download.file, isCompleted = false)
            }

            override fun onCancelled(download: Download) {
                File(download.file).delete()
                fetch.remove(download.id)
                releaseWakeLockIfIdle()
            }
        })
    }

    private fun notifyStateChanged(filePath: String, isCompleted: Boolean) {
        val intent = Intent("com.fix.engine.abdullah.DOWNLOAD_STATE_CHANGED").apply {
            setPackage(packageName)
            putExtra("FILE_PATH", filePath)
            putExtra("IS_COMPLETED", isCompleted)
        }
        sendBroadcast(intent)
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AbdullahStore::DownloadWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(15 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLockIfIdle() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        var appInstance: AbdullahStoreApp? = null
            private set

        fun isApkCompleteAndValid(context: Context, file: File): Boolean {
            if (!file.exists() || file.length() <= 0L) return false
            return try {
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) != null
            } catch (e: Exception) {
                false
            }
        }
    }
}
