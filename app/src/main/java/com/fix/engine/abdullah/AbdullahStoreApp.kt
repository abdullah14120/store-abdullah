package com.fix.engine.abdullah

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
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
 * Project: Abdullah Store - Direct APK Download Engine (No .tmp, No Resume)
 */
class AbdullahStoreApp : Application() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        val notificationManager = CustomFetchNotificationManager(this)

        // إعداد اتصال مباشر ومستقر لروابط Google Drive
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // إعداد Fetch بوضع الاتصال المتسلسل الواحد (SEQUENTIAL) بدون إعادة محاولات استئناف معقدة
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

        // مراقب مركزي يبقي التحميل نشطاً في الخلفية وينظف أي ملف غير مكتمل عند الإلغاء أو الفشل
        fetch.addListener(object : AbstractFetchListener() {
            override fun onStarted(download: Download, downloadBlocks: List<com.tonyodev.fetch2core.DownloadBlock>, totalBlocks: Int) {
                acquireWakeLock()
            }

            override fun onCompleted(download: Download) {
                releaseWakeLockIfIdle()
                notifyStateChanged(download.file, isCompleted = true)
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                // حذف الملف غير المكتمل فوراً وإزالته من الطابور لمنع التعليق
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
                wakeLock?.acquire(15 * 60 * 1000L) // حد أقصى 15 دقيقة للأمان
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
        /**
         * فحص ذكي وسريع للتأكد من أن ملف الـ APK مكتمل 100% وصالح للتثبيت
         */
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
