package com.fix.engine.abdullah.service

import android.content.Context
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
 * Project: Abdullah Store - Direct Progress Tracker
 */
class DownloadTracker(private val context: Context) {

    private var currentListener: AbstractFetchListener? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    fun startTracking(downloadId: Int, onProgress: (Int, String) -> Unit) {
        stopTracking()

        if (downloadId == -1) {
            onProgress(-1, "فشل في التحميل")
            return
        }

        val fetch = Fetch.getDefaultInstance()

        currentListener = object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (download.id == downloadId) {
                    val progress = download.progress.coerceIn(0, 99)
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
                    stopTracking()
                    uiScope.launch { onProgress(100, "اكتمل التحميل بنجاح ✨") }
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == downloadId) {
                    File(download.file).delete()
                    fetch.remove(download.id)
                    stopTracking()
                    uiScope.launch { onProgress(-1, "انقطع التحميل، يرجى المحاولة مجدداً") }
                }
            }

            override fun onCancelled(download: Download) {
                if (download.id == downloadId) {
                    File(download.file).delete()
                    fetch.remove(download.id)
                    stopTracking()
                }
            }
        }

        fetch.addListener(currentListener!!)
    }

    fun stopTracking() {
        currentListener?.let {
            try {
                Fetch.getDefaultInstance().removeListener(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentListener = null
        }
    }
}
