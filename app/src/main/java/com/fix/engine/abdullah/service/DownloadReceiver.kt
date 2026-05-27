package com.fix.engine.abdullah.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
                // 🟢 الطريقة القياسية والآمنة لجلب المسار الحقيقي للملف من نظام أندرويد
                val fileUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                cursor.close()

                fileUriString?.let { uriString ->
                    // تحويل الـ URI إلى مسار ملف حقيقي يفهمه المعالج
                    val fileUri = Uri.parse(uriString)
                    val filePath = fileUri.path ?: ""
                    
                    // تنظيف المسار في حال قراءته بشكل خاطئ من الـ DownloadManager الصارم
                    val cleanPath = if (filePath.startsWith("/external/")) {
                        val externalStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
                        filePath.replace("/external", externalStorage)
                    } else {
                        filePath
                    }

                    val tempFile = File(cleanPath)
                    
                    if (tempFile.exists()) {
                        // 1. منطق تغيير الاسم: تحويل الملف من مؤقت إلى APK حقيقي
                        if (tempFile.name.endsWith(".tmp")) {
                            val finalName = tempFile.name.removeSuffix(".tmp")
                            val finalFile = File(tempFile.parent, finalName)
                            
                            if (tempFile.renameTo(finalFile)) {
                                // 2. فتح المثبت للملف النهائي المعدل اسمه بنجاح
                                openInstaller(context, finalFile)
                            } else {
                                // في حال فشل تغيير الاسم، نحاول فتح الملف الحالي لمنع تجمد العملية
                                openInstaller(context, tempFile)
                            }
                        } else {
                            // إذا كان الملف لا يحمل لاحقة مؤقتة، نثبته مباشرة
                            openInstaller(context, tempFile)
                        }
                    } else {
                        // محاولة أخيرة كخطة بديلة للوصول للملف عبر الـ Uri المباشر إذا لم يتعرف عليه كملف ثابت
                        openInstallerUsingUri(context, fileUri)
                    }
                }
            } else {
                cursor.close()
            }
        }
    }

    private fun openInstaller(context: Context, file: File) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 🚀 التعديل الجوهري والديناميكي للتوافق مع minSdk 23 وحتى أندرويد 15
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // لأندرويد 7.0 فما فوق نستخدم الـ FileProvider مع الـ Authority المصححة
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider", // 👈 تم تصحيح الملحق هنا
                        file
                    )
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    // لأندرويد 6.0 يقرأ المسار المباشر بسلام وثبات مطلق
                    setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                }
            }

            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "اكتمل التحميل: يرجى الضغط على 'تثبيت الآن' من داخل المتجر")
        }
    }

    /**
     * 🛡️ دالة حماية احتياطية لتمرير الـ Uri مباشرة في حال لم تتعرف الجافا على المسار النصي للملف
     */
    private fun openInstallerUsingUri(context: Context, uri: Uri) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
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
