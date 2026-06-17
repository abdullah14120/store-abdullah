package com.fix.engine.abdullah.service

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.tonyodev.fetch2.EnqueueAction
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Refactored: Powered by Fetch API with Auto-Retry, Scoped Storage & Tags
 */
class AndroidDownloadManager(private val context: Context) {

    // 🚀 الاستدعاء الرسمي الآمن للمكتبة
    private val fetch = Fetch.getDefaultInstance()

    // 🚀 إضافة packageName كمتغير لمتابعة حالة التحميل في الواجهة
    fun enqueueDownload(url: String, fileName: String, packageName: String): Int {
        return try {
            // إضافة .tmp لضمان عدم قراءة النظام للملف قبل اكتماله
            val tempFileName = "$fileName.tmp"
            
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, tempFileName)
            val filePath = file.absolutePath

            val request = Request(url, filePath).apply {
                priority = Priority.HIGH
                networkType = NetworkType.ALL 
                autoRetryMaxAttempts = 10 
                enqueueAction = EnqueueAction.REPLACE_EXISTING
                // 🏷️ إضافة اسم الحزمة كـ Tag لتسهيل ربط التحميل بالتطبيق في الواجهة
                tag = packageName 
            }

            fetch.enqueue(request, { _ ->
                // تم وضع الطلب في طابور التحميل بنجاح
            }, { error ->
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "فشل بدء التحميل: ${error.name}", Toast.LENGTH_SHORT).show()
                }
            })

            request.id 
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1
        }
    }
}
