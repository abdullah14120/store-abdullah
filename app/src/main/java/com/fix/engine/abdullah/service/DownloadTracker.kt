package com.fix.engine.abdullah.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import java.io.File
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Performance Tracker & Auto Installer
 * Feature: Integrated FetchListener with Auto-Rename and Install
 */
class DownloadTracker(private val context: Context) {

    private val fetch = Fetch.Impl.getDefaultInstance()
    private var currentListener: AbstractFetchListener? = null
    private val handler = Handler(Looper.getMainLooper())

    fun startTracking(downloadId: Int, onProgress: (Int, String) -> Unit) {
        stopTracking() 

        if (downloadId == -1) {
            onProgress(0, "فشل في التحميل")
            return
        }

        currentListener = object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (download.id == downloadId) {
                    val progress = download.progress
                    
                    // حساب دقيق للميجابايت
                    val downloadedMb = download.downloaded / (1024.0 * 1024.0)
                    val totalMb = download.total / (1024.0 * 1024.0)
                    
                    val sizeText = if (download.total > 0) {
                        String.format(Locale.ENGLISH, "%.1f/%.1f MB", downloadedMb, totalMb)
                    } else {
                        "جاري جلب حجم الملف..."
                    }
                    
                    handler.post { onProgress(progress, sizeText) }
                }
            }

            override fun onCompleted(download: Download) {
                if (download.id == downloadId) {
                    handler.post { onProgress(100, "اكتمل التحميل") }
                    
                    // 🚀 دمج منطق التثبيت وتغيير الاسم فوراً بمجرد الاكتمال
                    processDownloadedFile(download.file)
                    stopTracking() 
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == downloadId) {
                    handler.post { onProgress(0, "فشل في التحميل") }
                    stopTracking()
                }
            }

            override fun onPaused(download: Download) {
                if (download.id == downloadId) {
                    handler.post { onProgress(download.progress, "تم الإيقاف مؤقتاً") }
                }
            }
        }

        fetch.addListener(currentListener!!)
    }

    fun stopTracking() {
        currentListener?.let {
            fetch.removeListener(it)
            currentListener = null
        }
    }

    /**
     * 🟢 تم نقل منطق DownloadReceiver القديم إلى هنا بشكل أنظف ومباشر
     */
    private fun processDownloadedFile(filePath: String) {
        val tempFile = File(filePath)
        
        if (tempFile.exists()) {
            if (tempFile.name.endsWith(".tmp")) {
                val finalName = tempFile.name.removeSuffix(".tmp")
                val finalFile = File(tempFile.parent, finalName)
                
                if (tempFile.renameTo(finalFile)) {
                    openInstaller(finalFile)
                } else {
                    openInstaller(tempFile)
                }
            } else {
                openInstaller(tempFile)
            }
        }
    }

    private fun openInstaller(file: File) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                }
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            handler.post {
                Toast.makeText(context, "اكتمل التحميل: يرجى الضغط على 'تثبيت الآن' من داخل المتجر", Toast.LENGTH_LONG).show()
            }
        }
    }
}
