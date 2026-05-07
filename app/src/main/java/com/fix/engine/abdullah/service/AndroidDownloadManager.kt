package com.fix.engine.abdullah.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Download Core
 * Fix: Changed 'setAllowedInRoaming' to 'setAllowedOverRoaming'
 */
class AndroidDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(url: String, fileName: String): Long {
        // تأكد من وجود المجلد أولاً لتجنب خطأ FileNotFound
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null && !downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val file = File(downloadDir, fileName)
        
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("FIX ENGINE")
                .setDescription("جاري تحميل: $fileName")
                
                // إظهار الإشعار أثناء وبعد التحميل
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // تحديد المسار باستخدام Uri.fromFile
                .setDestinationUri(Uri.fromFile(file))
                
                // السماح بالتحميل عبر بيانات الهاتف (مهم لمستخدمي الشبكات المتغيرة)
                .setAllowedOverMetered(true)
                
                // الإصلاح هنا: الدالة الصحيحة هي setAllowedOverRoaming
                .setAllowedOverRoaming(true) 

            downloadManager.enqueue(request)
        } catch (e: Exception) {
            // استخدام Handler لضمان ظهور التوست من أي Thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1L
        }
    }
}
