package com.fix.engine.abdullah.service

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Refactored: Powered by Fetch API for Multi-thread Downloading
 */
class AndroidDownloadManager(private val context: Context) {

    private val fetch = Fetch.Impl.getDefaultInstance()

    fun enqueueDownload(url: String, fileName: String): Int {
        return try {
            val tempFileName = "$fileName.tmp"
            
            // 🟢 استخدام مسار التطبيق المباشر لتجاوز مشاكل Scoped Storage في أندرويد 11+
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, tempFileName)
            val filePath = file.absolutePath

            val request = Request(url, filePath)
            request.priority = Priority.HIGH
            request.networkType = NetworkType.ALL // التحميل عبر الواي فاي أو البيانات

            fetch.enqueue(request, { _ ->
                // تم وضع الطلب في طابور التحميل بنجاح
            }, { error ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "فشل بدء التحميل: ${error.name}", Toast.LENGTH_SHORT).show()
                }
            })

            // Fetch يستخدم Int كمعرف (ID) بدلاً من Long
            request.id 
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1
        }
    }
}
