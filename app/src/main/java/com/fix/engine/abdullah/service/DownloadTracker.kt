package com.fix.engine.abdullah.service

import android.content.Context
import android.os.Environment
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
 * Feature: Integrated FetchListener with Auto-Rename & Clean Separation from AppDetailsActivity
 */
class DownloadTracker(private val context: Context) {

    private val fetch = Fetch.getDefaultInstance()
    private var currentListener: AbstractFetchListener? = null
    
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

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
                    
                    val speedKB = downloadedBytesPerSecond / 1024.0
                    val speedText = if (speedKB >= 1024.0) {
                        String.format(Locale.ENGLISH, "%.1f MB/s", speedKB / 1024.0)
                    } else {
                        String.format(Locale.ENGLISH, "%.1f KB/s", speedKB)
                    }

                    val sizeText = if (download.total > 0) {
                        String.format(Locale.ENGLISH, "%s • %.1f/%.1f MB", speedText, downloadedMb, totalMb)
                    } else {
                        String.format(Locale.ENGLISH, "%s • %.1f MB", speedText, downloadedMb)
                    }
                    
                    uiScope.launch { onProgress(progress, sizeText) }
                }
            }

            override fun onCompleted(download: Download) {
                if (download.id == downloadId) {
                    uiScope.launch { onProgress(100, "اكتمل التحميل بنجاح ✨") }
                    processCompletedFile(download.file)
                    stopTracking() 
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == downloadId) {
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

    /**
     * معالجة الملف بعد اكتماله: تغيير اسمه من .tmp إلى مساره النهائي بدون تشغيل التثبيت المزدوج
     * (تم نقل تشغيل PackageInstaller إلى AppDetailsActivity حصرياً لمنع تضارب الخيوط)
     */
    private fun processCompletedFile(filePath: String) {
        ioScope.launch {
            val tempFile = File(filePath)
            if (tempFile.exists() && tempFile.name.endsWith(".tmp")) {
                val finalName = tempFile.name.removeSuffix(".tmp")
                val finalFile = File(tempFile.parent, finalName)
                tempFile.renameTo(finalFile)
            }
        }
    }
}
