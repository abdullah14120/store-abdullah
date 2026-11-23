package com.fix.engine.abdullah.service

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.fix.engine.abdullah.data.network.LinkExtractor
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
 * Feature: Resilient APK Download with Identity Encoding, Session Cookie Forwarding & Auto-Retry Resume
 */
class AndroidDownloadManager(private val context: Context) {

    fun enqueueDownload(url: String, fileName: String, packageName: String): Int {
        return try {
            val fetch = Fetch.getDefaultInstance()
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

            val file = File(dir, fileName)
            // تنظيف أي ملف .tmp قديم إن وجد من إصدارات سابقة
            File(dir, "$fileName.tmp").delete()

            val filePath = file.absolutePath

            val request = Request(url, filePath).apply {
                priority = Priority.HIGH
                networkType = NetworkType.ALL
                // 🛡️ إعادة المحاولة التلقائية مع استئناف الجزء المحمّل (HTTP Range) عند ضعف أو تقطع الإنترنت
                autoRetryMaxAttempts = 5
                enqueueAction = EnqueueAction.REPLACE_EXISTING
                tag = packageName
                addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                // مستوحى من gdown: ضمان تطابق أرقام البايتات على القرص مع خادم Google Drive عند استئناف التحميل
                addHeader("Accept-Encoding", "identity")
                addHeader("Connection", "keep-alive")

                // تمرير كوكيز جلسة التأكيد التي التقطها LinkExtractor لتفادي رفض خادم Google Drive
                val cookies = LinkExtractor.getCookieHeader(url)
                if (!cookies.isNullOrBlank()) {
                    addHeader("Cookie", cookies)
                }
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
