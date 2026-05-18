package com.fix.engine.abdullah.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Modern Session Installer with Automatic Fallback Path Support
 */
class SessionInstaller(private val context: Context) {

    private val packageInstaller: PackageInstaller = context.packageManager.packageInstaller

    fun installApk(apkFile: File, packageName: String, onProgressUpdate: (Int) -> Unit): Boolean {
        if (!apkFile.exists()) return false

        var session: PackageInstaller.Session? = null
        try {
            // 1. إعداد بارامترات الجلسة الجديدة
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // السطر السحري لأندرويد 12+: يطلب التثبيت الصامت دون إزعاج المستخدم إذا كان تحديثاً
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            // 2. إنشاء الجلسة وحفظ الـ ID الخاص بها
            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            // 3. فتح مجرى البيانات لنسخ ملف الـ APK إلى الجلسة
            val fileSize = apkFile.length()
            val inputStream = FileInputStream(apkFile)
            val outputStream: OutputStream = session.openWrite("fix_engine_install_$packageName", 0, fileSize)

            val buffer = ByteArray(65536)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // حساب نسبة تقدم الحقن بالخلفية وإرسالها للواجهة (0 إلى 100)
                val progress = ((totalBytesRead * 100) / fileSize).toInt()
                onProgressUpdate(progress)
            }

            session.fsync(outputStream)
            inputStream.close()
            outputStream.close()

            // 4. إنشاء PendingIntent ليعمل كمستقبل للنتيجة النهائية من النظام (نجاح أم فشل)
            val intent = Intent(context, InstallStatusReceiver::class.java).apply {
                action = "com.fix.engine.abdullah.COMMIT_INSTALL"
                putExtra("PACK_NAME", packageName)
                putExtra("APK_PATH", apkFile.absolutePath) // ⬅️ تم إضافة هذا السطر لتمرير مسار الملف لخطة الإنقاذ (Plan C)
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            // 5. إرسال الجلسة للنظام للتنفيذ النهائي
            session.commit(pendingIntent.intentSender)
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            session?.abandon()
            return false
        } finally {
            session?.close()
        }
    }
}
