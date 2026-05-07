package com.fix.engine.abdullah.service

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

class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                installDownloadedPackage(context, downloadId)
            }
        }
    }

    private fun installDownloadedPackage(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                
                // الطريقة الأكثر أماناً لجلب الملف في الأنظمة الحديثة
                val fileUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                cursor.close() // نغلق الكرسي فوراً بعد جلب البيانات

                fileUriString?.let { uriString ->
                    val fileUri = Uri.parse(uriString)
                    // التحويل من Uri إلى File يحتاج معالجة للمسارات التي تبدأ بـ file://
                    val filePath = fileUri.path
                    if (filePath != null) {
                        val file = File(filePath)
                        if (file.exists()) {
                            openInstaller(context, file)
                        } else {
                            showToast(context, "لم يتم العثور على ملف APK")
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
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // إضافة flag إضافي لضمان الظهور فوق التطبيقات الأخرى
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            showToast(context, "فشل التثبيت: تأكد من منح صلاحية تثبيت التطبيقات")
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
