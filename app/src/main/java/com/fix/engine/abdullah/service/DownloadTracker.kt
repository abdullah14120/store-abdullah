package com.fix.engine.abdullah.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Performance Tracker & Auto Installer
 * Feature: Integrated FetchListener with Auto-Rename & PackageInstaller Dispatching
 */
class DownloadTracker(private val context: Context) {

    // 🚀 الاستدعاء الآمن والرسمي لمكتبة Fetch
    private val fetch = Fetch.getDefaultInstance()
    private var currentListener: AbstractFetchListener? = null
    
    // 🚀 استخدام Coroutines للتواصل السريع والآمن مع خيط الواجهة والعمليات الثقيلة
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun startTracking(downloadId: Int, onProgress: (Int, String) -> Unit) {
        stopTracking() 

        if (downloadId == -1) {
            onProgress(0, "فشل في التحميل")
            return
        }

        currentListener = object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (download.id == downloadId) {
                    val progress = download.progress
                    val downloadedMb = download.downloaded / (1024.0 * 1024.0)
                    val totalMb = download.total / (1024.0 * 1024.0)
                    
                    val sizeText = if (download.total > 0) {
                        String.format(Locale.ENGLISH, "%.1f/%.1f MB", downloadedMb, totalMb)
                    } else {
                        "جاري جلب حجم الملف..."
                    }
                    
                    uiScope.launch { onProgress(progress, sizeText) }
                }
            }

            override fun onCompleted(download: Download) {
                if (download.id == downloadId) {
                    uiScope.launch { onProgress(100, "جاري التجهيز للتثبيت...") }
                    processDownloadedFile(download.file)
                    stopTracking() 
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == downloadId) {
                    // 🛡️ إذا وصلنا هنا، يعني أن الـ 10 محاولات التلقائية لـ Fetch فشلت.
                    uiScope.launch { onProgress(0, "فشل التحميل. يرجى المحاولة لاحقاً.") }
                    stopTracking()
                }
            }

            override fun onPaused(download: Download) {
                if (download.id == downloadId) {
                    uiScope.launch { onProgress(download.progress, "تم الإيقاف مؤقتاً") }
                }
            }
        }

        fetch.addListener(currentListener!!)
    }

    fun stopTracking() {
        currentListener?.let {
            fetch.removeListener(it)
            currentListener = null
        }
    }

    /**
     * معالجة الملف بعد اكتماله: تغيير اسمه من .tmp إلى مساره النهائي ثم إرساله للتثبيت
     */
    private fun processDownloadedFile(filePath: String) {
        ioScope.launch {
            val tempFile = File(filePath)
            
            if (tempFile.exists()) {
                val targetFile = if (tempFile.name.endsWith(".tmp")) {
                    val finalName = tempFile.name.removeSuffix(".tmp")
                    val finalFile = File(tempFile.parent, finalName)
                    // تغيير الاسم، وإذا فشل لأي سبب نستخدم الملف المؤقت كما هو
                    if (tempFile.renameTo(finalFile)) finalFile else tempFile
                } else {
                    tempFile
                }

                // 🚀 تشغيل التثبيت المتقدم
                installPackageAdvanced(targetFile)
            }
        }
    }

    /**
     * 🚀 محرك التثبيت المتقدم الخاص بمتجر Abdullah 
     * يعتمد على PackageInstaller API لتقديم تجربة تثبيت سلسة ومدمجة.
     */
    private suspend fun installPackageAdvanced(apkFile: File) {
        withContext(Dispatchers.IO) {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                
                // 💡 بدءاً من أندرويد 12، يمكن طلب التحديث الصامت إذا كان متجرك هو المالك
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }

                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                // 🔄 نسخ بيانات ملف الـ APK إلى جلسة التثبيت الآمنة الخاصة بالنظام
                val sizeBytes = apkFile.length()
                FileInputStream(apkFile).use { inputStream ->
                    session.openWrite("StoreInstallSession", 0, sizeBytes).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        session.fsync(outputStream)
                    }
                }

                // 📡 إعداد الـ Intent لإخبار النظام أين يرسل نتيجة التثبيت (إلى InstallStatusReceiver)
                val intent = Intent(context, com.fix.engine.abdullah.installer.InstallStatusReceiver::class.java).apply {
                    action = "com.fix.engine.abdullah.COMMIT_INSTALL"
                }

                // ⚠️ يجب استخدام FLAG_MUTABLE هنا لكي يتمكن نظام أندرويد من حقن حالة التثبيت
                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    pendingIntentFlags
                )

                // 🚀 تنفيذ أمر التثبيت وإغلاق الجلسة
                session.commit(pendingIntent.intentSender)
                session.close()

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "فشل تهيئة محرك التثبيت", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
