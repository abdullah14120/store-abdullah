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
 * Project: Abdullah Store - Auto Installer
 * Feature: Secure FileProvider Integration & Public Path Handling
 */
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
                
                // جلب المسار المحلي للملف من قاعدة بيانات التنزيلات
                val localUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                cursor.close()

                localUriString?.let { uriString ->
                    val fileUri = Uri.parse(uriString)
                    val filePath = fileUri.path
                    
                    if (filePath != null) {
                        // في بعض الأجهزة، المسار قد يحتوي على بادئة file://، نقوم بتنظيفها
                        val cleanPath = filePath.replace("external_files", "storage/emulated/0")
                        val file = File(cleanPath)
                        
                        if (file.exists()) {
                            openInstaller(context, file)
                        } else {
                            // محاولة بديلة: الوصول للملف عبر الـ Uri مباشرة
                            handleLegacyFile(context, fileUri)
                        }
                    }
                }
            } else {
                cursor.close()
            }
        }
    }

    private fun handleLegacyFile(context: Context, uri: Uri) {
        val file = File(uri.path ?: "")
        if (file.exists()) {
            openInstaller(context, file)
        } else {
            showToast(context, "اكتمل التحميل، يرجى التثبيت من مدير الملفات")
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
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            showToast(context, "فشل فتح المثبت: يرجى منح صلاحية تثبيت التطبيقات غير المعروفة")
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
