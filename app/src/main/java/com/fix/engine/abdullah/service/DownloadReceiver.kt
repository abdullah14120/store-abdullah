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

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Auto Installer Receiver
 */
class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                // استرجاع مسار الملف المحمل من نظام أندرويد
                installDownloadedPackage(context, downloadId)
            }
        }
    }

    private fun installDownloadedPackage(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUriString = cursor.getString(localUriIndex)
                
                if (localUriString != null) {
                    val fileUri = Uri.parse(localUriString)
                    val file = File(fileUri.path ?: "")

                    if (file.exists()) {
                        openInstaller(context, file)
                    } else {
                        // في بعض الأجهزة قد يكون المسار بتنسيق مختلف، نحاول جلب المسار الحقيقي
                        Toast.makeText(context, "اكتمل التحميل، يرجى النقر على الإشعار للتثبيت", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        cursor.close()
    }

    private fun openInstaller(context: Context, file: File) {
        try {
            // استخدام FileProvider لمنح صلاحية القراءة لمثبت الحزم
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "فشل بدء التثبيت: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
