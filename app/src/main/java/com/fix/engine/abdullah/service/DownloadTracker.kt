package com.fix.engine.abdullah.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Performance Tracker & Auto Installer
 * Feature: Integrated FetchListener with Auto-Rename & Safe Intent Dispatching
 */
class DownloadTracker(private val context: Context) {

    // 🚀 الاستدعاء الآمن والرسمي
    private val fetch = Fetch.getDefaultInstance()
    private var currentListener: AbstractFetchListener? = null
    
    // 🚀 استخدام Coroutines للتواصل السريع والآمن مع خيط الواجهة
    private val uiScope = CoroutineScope(Dispatchers.Main)

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
                    val downloadedMb = download.downloaded / (1024.0 * 1024.0)
                    val totalMb = download.total / (1024.0 * 1024.0)
                    
                    val sizeText = if (download.total > 0) {
                        String.format(Locale.ENGLISH, "%.1f/%.1f MB", downloadedMb, totalMb)
                    } else {
                        "جاري جلب حجم الملف..."
                    }
                    
                    uiScope.launch { onProgress(progress, sizeText) }
                }
            }

            override fun onCompleted(download: Download) {
                if (download.id == downloadId) {
                    uiScope.launch { onProgress(100, "اكتمل التحميل") }
                    processDownloadedFile(download.file)
                    stopTracking() 
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == downloadId) {
                    // 🛡️ إذا وصلنا هنا، يعني أن الـ 10 محاولات التلقائية فشلت.
                    // نكتفي بإبلاغ المستخدم دون الدخول في حلقة لا نهائية.
                    uiScope.launch { onProgress(0, "فشل التحميل. يرجى المحاولة لاحقاً.") }
                    stopTracking()
                }
            }

            override fun onPaused(download: Download) {
                if (download.id == downloadId) {
                    uiScope.launch { onProgress(download.progress, "تم الإيقاف مؤقتاً") }
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
            // 🛡️ معالجة الفشل بصمت إذا كان التطبيق في الخلفية (قيود أندرويد 10+)
            uiScope.launch {
                Toast.makeText(context, "اكتمل التحميل. اضغط على التطبيق للتثبيت", Toast.LENGTH_LONG).show()
            }
        }
    }
}
