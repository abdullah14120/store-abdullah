package com.fix.engine.abdullah.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.fix.engine.abdullah.data.model.AppModel
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import java.io.File
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Advanced Download & Install Manager
 */
class DownloadManager(private val context: Context, private val fetch: Fetch) {

    fun startDownload(app: AppModel, onProgress: (Int, String, String) -> Unit) {
        // تحديد مسار التحميل داخل مجلد التطبيق الخاص لضمان الصلاحيات
        val downloadDir = File(context.getExternalFilesDir(null), "downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destination = "${downloadDir.absolutePath}/${app.packageName}.apk"
        
        val request = Request(app.downloadUrl, destination).apply {
            priority = Priority.HIGH
            networkType = NetworkType.ALL
            // إضافة المعرف لربطه بالتطبيق
            addHeader("User-Agent", "FixEngine/1.0")
        }

        fetch.enqueue(request, { updatedRequest ->
            // نجاح إضافة الطلب للطابور
        }, { error ->
            Toast.makeText(context, "فشل بدء التحميل: ${error.name}", Toast.LENGTH_SHORT).show()
        })

        // مراقب التحميل (Listener)
        fetch.addListener(object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (request.id == download.id) {
                    val progress = download.progress
                    val eta = formatEta(etaInMilliSeconds)
                    val speed = formatSpeed(downloadedBytesPerSecond)
                    onProgress(progress, eta, speed)
                }
            }

            override fun onCompleted(download: Download) {
                if (request.id == download.id) {
                    // تشغيل التثبيت التلقائي فور الاكتمال
                    installApk(File(download.file))
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (request.id == download.id) {
                    Toast.makeText(context, "حدث خطأ أثناء التحميل", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun formatEta(milli: Long): String {
        if (milli <= 0) return "00:00"
        val seconds = (milli / 1000) % 60
        val minutes = (milli / (1000 * 60)) % 60
        return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        val kbps = bytesPerSecond.toDouble() / 1024
        val mbps = kbps / 1024
        return if (mbps >= 1) {
            String.format(Locale.ENGLISH, "%.1f MB/s", mbps)
        } else {
            String.format(Locale.ENGLISH, "%.0f KB/s", kbps)
        }
    }
    
    private fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "فشل فتح ملف التثبيت", Toast.LENGTH_LONG).show()
        }
    }
}
