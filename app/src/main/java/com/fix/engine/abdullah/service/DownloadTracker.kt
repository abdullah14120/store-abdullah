package com.fix.engine.abdullah.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper

class DownloadTracker(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    @SuppressLint("Range")
    fun startTracking(downloadId: Long, onProgress: (Int, String) -> Unit) {
        runnable = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        onProgress(100, "اكتمل")
                        stopTracking()
                    } else if (bytesTotal > 0) {
                        val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                        val sizeInMb = String.format("%.1f/%.1f MB", bytesDownloaded / 1024f / 1024f, bytesTotal / 1024f / 1024f)
                        onProgress(progress, sizeInMb)
                    }
                }
                cursor.close()
                // تحديث كل 500 ملي ثانية لتكون الحركة سلسة
                handler.postDelayed(this, 500)
            }
        }
        handler.post(runnable!!)
    }

    fun stopTracking() {
        runnable?.let { handler.removeCallbacks(it) }
    }
}
