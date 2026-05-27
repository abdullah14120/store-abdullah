package com.fix.engine.abdullah.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Temporary File Extension (.tmp) to avoid premature APK detection
 */
class AndroidDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * يقوم ببدء عملية التحميل مع إضافة لاحقة مؤقتة لاسم الملف.
     * سيتم تغيير الاسم لللاحقة الأصلية (.apk) عبر الـ DownloadReceiver عند الاكتمال.
     */
    fun enqueueDownload(url: String, fileName: String): Long {
        return try {
            // إضافة لاحقة مؤقتة لمنع اكتشاف الملف كـ APK جاهز قبل اكتماله
            val tempFileName = "$fileName.tmp"

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName) // العنوان الذي يظهر في الإشعارات
                .setDescription("جاري تحميل التحديث عبر متجر عبدالله...")
                
                // إظهار الإشعار أثناء التحميل وعند الاكتمال
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // 🟢 التعديل الجوهري والأمثل: التخزين في مجلد التحميلات التابع للتطبيق لمنع مشاكل الصلاحيات في الأنظمة الحديثة
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, tempFileName)
                
                // إعدادات الاتصال الشاملة
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true) 

            // 🚀 لإجبار معالج الحزم والنظام على رؤية الملف وفحصه في الأنظمة القديمة والحديثة
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                request.setVisibleInDownloadsUi(true)
            }

            downloadManager.enqueue(request)
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1L
        }
    }
}
