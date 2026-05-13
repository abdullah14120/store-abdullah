package com.fix.engine.abdullah.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Auto Installer & File Renamer
 * Feature: Automatic renaming from .tmp to .apk upon completion
 */
class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                processDownloadedFile(context, downloadId)
            }
        }
    }

    @SuppressLint("Range")
    private fun processDownloadedFile(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                // جلب مسار الملف المؤقت الذي ينتهي بـ .tmp
                val localUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                cursor.close()

                localUriString?.let { uriString ->
                    val tempFile = File(Uri.parse(uriString).path ?: "")
                    
                    if (tempFile.exists()) {
                        // 1. منطق تغيير الاسم: تحويل الملف من مؤقت إلى APK حقيقي
                        if (tempFile.name.endsWith(".tmp")) {
                            val finalName = tempFile.name.removeSuffix(".tmp")
                            val finalFile = File(tempFile.parent, finalName)
                            
                            if (tempFile.renameTo(finalFile)) {
                                // 2. فتح المثبت للملف النهائي
                                openInstaller(context, finalFile)
                            } else {
                                // في حال فشل تغيير الاسم، نحاول فتح الملف الحالي
                                openInstaller(context, tempFile)
                            }
                        } else {
                            // إذا كان الملف لا يحمل لاحقة مؤقتة، نثبته مباشرة
                            openInstaller(context, tempFile)
                        }
                    }
                }
            } else {
                cursor.close()
            }
        }
    }

    private fun openInstaller(context: Context, file: File) {
        try {
            // استخدام FileProvider للحصول على URI آمن للتثبيت
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                // تحديد نوع الملف كحزمة أندرويد (APK)
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            showToast(context, "اكتمل التحميل: يرجى الضغط على 'تثبيت الآن' من داخل المتجر")
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
