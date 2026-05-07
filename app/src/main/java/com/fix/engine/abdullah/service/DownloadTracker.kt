package com.fix.engine.abdullah.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import java.util.Locale

class DownloadTracker(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    @SuppressLint("Range")
    fun startTracking(downloadId: Long, onProgress: (Int, String) -> Unit) {
        // نوقف أي تتبع سابق لنفس الكائن لضمان عدم التداخل
        stopTracking()

        runnable = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)
                
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            onProgress(100, "اكتمل التحميل")
                            stopTracking()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            onProgress(0, "فشل التحميل")
                            stopTracking()
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                            if (bytesTotal > 0) {
                                val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                                // استخدام Locale.ENGLISH لضمان ظهور الأرقام بشكل صحيح في كل الأنظمة
                                val sizeInMb = String.format(Locale.ENGLISH, "%.1f/%.1f MB", 
                                    bytesDownloaded / 1024f / 1024f, 
                                    bytesTotal / 1024f / 1024f)
                                onProgress(progress, sizeInMb)
                            }
                        }
                    }
                }
                cursor?.close()

                // استمرار التتبع فقط إذا كان الـ runnable لا يزال نشطاً
                runnable?.let {
                    handler.postDelayed(it, 500)
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
