package com.fix.engine.abdullah.service

import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.util.Log
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import java.io.File

class DownloadManager(private val fetch: Fetch) {

    fun startDownload(app: AppModel, downloadDir: String, onProgress: (Int, String, String) -> Unit) {
        val file = "$downloadDir/${app.name}.apk"
        val request = Request(app.downloadUrl, file).apply {
            priority = Priority.HIGH
            networkType = NetworkType.ALL
            // إضافة Headers إذا لزم الأمر
        }

        fetch.enqueue(request, { updatedRequest ->
            Log.i("بدأ تحميل: ${app.name}")
        }, { error ->
            Log.e("خطأ في التحميل: ${error.name}")
        })

        // مراقب التحميل (Listener)
        fetch.addListener(object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (request.id == download.request.id) {
                    val progress = download.progress
                    val eta = formatEta(etaInMilliSeconds) // دالة لتحويل الوقت
                    val speed = formatSpeed(downloadedBytesPerSecond) // دالة لتحويل السرعة
                    onProgress(progress, eta, speed)
                }
            }

            override fun onCompleted(download: Download) {
                Log.i("اكتمل التحميل: ${download.file}")
                // هنا نستدعي دالة التثبيت التلقائي
                installApk(download.file)
            }
        })
    }

    private fun formatEta(milli: Long): String {
        val seconds = milli / 1000
        return if (seconds < 60) "$seconds ثانية" else "${seconds / 60} دقيقة"
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        val mbps = bytesPerSecond.toDouble() / (1024 * 1024)
        return String.format("%.2f MB/s", mbps)
    }
    
    private fun installApk(filePath: String) {
        // كود فتح الـ Intent الخاص بالـ Package Installer
    }
}
