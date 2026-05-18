package com.fix.engine.abdullah.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Advanced Status Receiver with Custom Error Messaging (Space & Signature)
 */
class InstallStatusReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra("PACK_NAME") ?: "التطبيق"
        val apkPath = intent.getStringExtra("APK_PATH")
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        // إرسال برودكاست داخلي لإخفاء شريط التقدم في واجهة التفاصيل فور انتهاء العملية
        val updateUiIntent = Intent("com.fix.engine.abdullah.UPDATE_INSTALL_UI")
        context.sendBroadcast(updateUiIntent)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "تم تثبيت $packageName بنجاح! 🎉", Toast.LENGTH_SHORT).show()
            }
            
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }
            
            // الفشل بسبب نقص مساحة التخزين الجهاز (كود الحظر الافتراضي لأندرويد)
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Toast.makeText(context, "❌ فشل التثبيت: مساحة تخزين الهاتف غير كافية لـ $packageName!", Toast.LENGTH_LONG).show()
            }
            
            // الفشل بسبب عدم تطابق الحزم أو تعارض التوقيع (Signature/Incompatible Conflict)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Toast.makeText(context, "⚠️ فشل التثبيت: النسخة الحالية لا تتطابق مع النسخة المثبتة (تعارض في الحزمة)!", Toast.LENGTH_LONG).show()
            }
            
            else -> {
                // فحص نصوص الأخطاء الإضافية للتأكيد (في حال اختلاف الواجهات)
                when {
                    message.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", true) -> {
                        Toast.makeText(context, "❌ عذراً، لا توجد مساحة كافية على الهاتف لتثبيت هذا التطبيق!", Toast.LENGTH_LONG).show()
                    }
                    message.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", true) || message.contains("signatures do not match", true) -> {
                        Toast.makeText(context, "⚠️ تعارض في الحزمة! يرجى حذف الإصدار القديم وتثبيت التحديث من جديد.", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        // تشغيل الخطة ج (الإنقاذ الافتراضي) إذا كان الخطأ مجرد قيد واجهة وليس نقص مساحة أو تعارض
                        if (!apkPath.isNullOrEmpty()) {
                            val file = File(apkPath)
                            if (file.exists()) {
                                Toast.makeText(context, "تم تشغيل مثبت الإنقاذ لتجاوز قيود الواجهة 🛠️", Toast.LENGTH_LONG).show()
                                launchLegacyInstaller(context, file)
                            }
                        } else {
                            Toast.makeText(context, "فشل التثبيت: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun launchLegacyInstaller(context: Context, file: File) {
        try {
            val providerAuthority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, providerAuthority, file)
            val legacyIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(legacyIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
