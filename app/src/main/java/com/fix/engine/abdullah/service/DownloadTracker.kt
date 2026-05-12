package com.fix.engine.abdullah.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Performance Tracker
 * Feature: Large APK Support & Memory Optimization
 */
class DownloadTracker(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    @SuppressLint("Range")
    fun startTracking(downloadId: Long, onProgress: (Int, String) -> Unit) {
        stopTracking()

        runnable = object : Runnable {
            override fun run() {
                if (downloadId == -1L) return

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)
                var shouldContinue = true 

                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                onProgress(100, "اكتمل التحميل")
                                shouldContinue = false
                            }
                            DownloadManager.STATUS_FAILED -> {
                                onProgress(0, "فشل في التحميل")
                                shouldContinue = false
                            }
                            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                                if (bytesTotal > 0) {
                                    val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                                    val sizeInMb = String.format(Locale.ENGLISH, "%.1f/%.1f MB", 
                                        bytesDownloaded / 1024f / 1024f, 
                                        bytesTotal / 1024f / 1024f)
                                    onProgress(progress, sizeInMb)
                                } else {
                                    onProgress(0, "جاري البدء...")
                                }
                            }
                        }
                    } else {
                        shouldContinue = false
                    }
                } catch (e: Exception) {
                    shouldContinue = false
                } finally {
                    cursor?.close()
                }

                if (shouldContinue && runnable != null) {
                    handler.postDelayed(this, 800) // زيادة المهلة قليلاً لتقليل استهلاك المعالج
                }
            }
        }
        handler.post(runnable!!)
    }

    fun stopTracking() {
        runnable?.let {
            handler.removeCallbacks(it)
            runnable = null
        }
    }
}
