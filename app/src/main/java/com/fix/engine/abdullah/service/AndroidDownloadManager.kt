package com.fix.engine.abdullah.service

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.tonyodev.fetch2.EnqueueAction
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Refactored: Powered by Fetch API with Auto-Retry & Ghost Download Prevention
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
            
            // 🚀 التعديل الأول: تفعيل الاستئناف التلقائي بشراسة (محاولة إعادة الاتصال 10 مرات عند انقطاع الشبكة)
            request.autoRetryMaxAttempts = 10 
            
            // 🚀 التعديل الثاني: استبدال أي طلب قديم عالق بنفس المسار لتفادي تكرار التحميلات ودمج الملفات الخاطئ
            request.enqueueAction = EnqueueAction.REPLACE_EXISTING

            fetch.enqueue(request, { _ ->
                // تم وضع الطلب في طابور التحميل بنجاح
            }, { error ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "فشل بدء التحميل: ${error.name}", Toast.LENGTH_SHORT).show()
                }
            })

            // Fetch يستخدم Int كمعرف (ID) بدلاً من Long
            request.id 
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1
        }
    }
}
