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
 * Feature: Direct Clean APK Download (No .tmp, No Resume)
 */
class AndroidDownloadManager(private val context: Context) {

    fun enqueueDownload(url: String, fileName: String, packageName: String): Int {
        return try {
            val fetch = Fetch.getDefaultInstance()
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            
            // الحفظ المباشر باسم ملف الـ APK النهائي بدون .tmp
            val file = File(dir, fileName)
            if (file.exists()) {
                file.delete()
            }
            // تنظيف أي ملف .tmp قديم إن وجد من إصدارات سابقة
            File(dir, "$fileName.tmp").delete()

            val filePath = file.absolutePath

            val request = Request(url, filePath).apply {
                priority = Priority.HIGH
                networkType = NetworkType.ALL
                autoRetryMaxAttempts = 0
                // بدء تنزيل نظيف دائماً واستبدال أي محاولة سابقة
                enqueueAction = EnqueueAction.REPLACE_EXISTING
                tag = packageName
                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            }

            fetch.enqueue(request, { _ ->
                // بدأ التنزيل بنجاح
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
