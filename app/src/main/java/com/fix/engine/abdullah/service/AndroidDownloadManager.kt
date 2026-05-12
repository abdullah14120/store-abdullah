package com.fix.engine.abdullah.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Download Core
 * Feature: Public Directory Storage & Unique Naming Support
 */
class AndroidDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(url: String, fileName: String): Long {
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("متجر Abdullah")
                .setDescription("جاري تحميل: $fileName")
                
                // إظهار الإشعار أثناء وبعد التحميل لسهولة الوصول
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // التخزين في مجلد التنزيلات العام ليكون مرئياً للمستخدم ولنظام التثبيت
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                
                // السماح بالتحميل عبر كافة أنواع الشبكات (Wifi + Data)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true) 

            downloadManager.enqueue(request)
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1L
        }
    }
}
