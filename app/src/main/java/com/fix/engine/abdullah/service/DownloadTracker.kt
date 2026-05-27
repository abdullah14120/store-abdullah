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
 * Feature: Large APK Support & Memory Optimization (Leak-Free)
 */
class DownloadTracker(context: Context) {

    // 🟢 استخدامgetApplicationContext لمنع تسريب الذاكرة (Memory Leak) نهائياً في حال إغلاق الـ Activity
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    @SuppressLint("Range")
    fun startTracking(downloadId: Long, onProgress: (Int, String) -> Unit) {
        stopTracking() // ضمان إيقاف أي عداد برمي سابق قبل البدء

        if (downloadId == -1L) {
            onProgress(0, "فشل في التحميل")
            return
        }

        runnable = object : Runnable {
            override fun run() {
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
                            DownloadManager.STATUS_RUNNING -> {
                                if (bytesTotal > 0) {
                                    val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                                    
                                    // 🟢 معالجة رياضية فائقة الدقة لحساب الـ ميجابايت للتطبيقات الضخمة بدون أخطاء الـ Float
                                    val downloadedMb = bytesDownloaded.toDouble() / (1024.0 * 1024.0)
                                    val totalMb = bytesTotal.toDouble() / (1024.0 * 1024.0)
                                    
                                    val sizeInMb = String.format(Locale.ENGLISH, "%.1f/%.1f MB", downloadedMb, totalMb)
                                    onProgress(progress, sizeInMb)
                                } else {
                                    onProgress(0, "جاري جلب حجم الملف...")
                                }
                            }
                            DownloadManager.STATUS_PENDING -> {
                                onProgress(0, "جاري الانتظار في الصف...")
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                onProgress(0, "تم إيقاف التحميل مؤقتاً")
                            }
                        }
                    } else {
                        // في حال إلغاء التحميل من قبل المستخدم واختفاء الـ ID، نبلغ الواجهة فوراً للتراجع
                        onProgress(0, "تم إلغاء التحميل")
                        shouldContinue = false
                    }
                } catch (e: Exception) {
                    onProgress(0, "فشل في التحميل")
                    shouldContinue = false
                } finally {
                    cursor?.close()
                }

                // الاستمرار في الفحص وإعادة جدولة الـ Runnable كل 800 مللي ثانية لحفظ المعالج والبطارية
                if (shouldContinue && runnable != null) {
                    handler.postDelayed(this, 800)
                }
            }
        }
        handler.post(runnable!!)
    }

    /**
     * 🛡️ دالة الحظر المباشر لتنظيف الذاكرة العشوائية ومنع الهدر الخلفي بالـ Runtime
     */
    fun stopTracking() {
        runnable?.let {
            handler.removeCallbacks(it)
            runnable = null
        }
    }
}
