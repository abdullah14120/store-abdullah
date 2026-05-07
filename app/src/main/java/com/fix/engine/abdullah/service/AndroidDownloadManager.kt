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
 * Fix: Corrected 'setAllowedOverRoaming' and added directory safety checks.
 */
class AndroidDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(url: String, fileName: String): Long {
        // 1. تحديد مجلد التحميل الرسمي للتطبيق (Download)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        
        // 2. التأكد من أن المجلد موجود فعلياً في ذاكرة النظام
        if (downloadDir != null && !downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val file = File(downloadDir, fileName)
        
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("FIX ENGINE")
                .setDescription("جاري تحميل: $fileName")
                
                // إعدادات التنبيهات والظهور في الإشعارات
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // تحديد المسار: نستخدم Uri.fromFile مع ملفات الـ ExternalFilesDir
                .setDestinationUri(Uri.fromFile(file))
                
                // إعدادات الشبكة (مهمة جداً للمستخدمين في اليمن)
                .setAllowedOverMetered(true) // السماح بالتحميل عبر بيانات الهاتف (Mobile Data)
                .setAllowedOverRoaming(true) // تم إصلاحها هنا (Correct function name)
                
                // جعل الملف قابلاً للفحص بواسطة نظام أندرويد لزيادة الأمان
                .setAllowedInRoaming(true) // هذه هي الدالة الصحيحة لبيانات التجوال

            // إضافة الطلب إلى طابور النظام وإرجاع المعرف (ID)
            downloadManager.enqueue(request)
            
        } catch (e: Exception) {
            // إظهار رسالة خطأ واضحة للمستخدم في حال فشل الرابط
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "خطأ في بدء التحميل: ${e.message}", Toast.LENGTH_LONG).show()
            }
            -1L // نرجع -1 للدلالة على الفشل
        }
    }
}
