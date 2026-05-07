package com.fix.engine.abdullah.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File

class AndroidDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(url: String, fileName: String): Long {
        // تأكد من وجود المجلد أولاً لتجنب خطأ FileNotFound
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir?.exists() == false) {
            downloadDir.mkdirs()
        }

        val file = File(downloadDir, fileName)
        
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("FIX ENGINE")
                .setDescription("جاري تحميل $fileName...")
                // إظهار الإشعار أثناء التحميل وبعد الانتهاء
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // استخدام Uri.fromFile سليم هنا لأن DownloadManager يعمل بصلاحيات النظام
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                // إضافة هذه لضمان أن النظام سيفحص الملف بحثاً عن الفيروسات أو الوسائط (اختياري)
                .setAllowedInRoaming(true)

            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            -1L
        }
    }
}
