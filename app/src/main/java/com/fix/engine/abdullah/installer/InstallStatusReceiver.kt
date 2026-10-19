package com.fix.engine.abdullah.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah (Store Engine)
 * Feature: Advanced PackageInstaller Status Receiver (Android 13/14+ Explicit Intent Compliance)
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // التأكد من أن هذا الـ Broadcast قادم من عملية التثبيت الخاصة بنا
        if (intent.action != "com.fix.engine.abdullah.COMMIT_INSTALL") {
            return
        }

        // استخراج حالة التثبيت والرسالة واسم الحزمة
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "بدون رسالة"
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "تطبيق غير معروف"

        when (status) {
            // 🟢 الحالة الأولى: المستخدم وافق، وتم التثبيت في النظام بنجاح!
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "تم التثبيت بنجاح! ✨", Toast.LENGTH_SHORT).show()
                Log.i("InstallReceiver", "Successfully installed: $packageName")
                
                // إبلاغ الواجهة بالنجاح مع تحديد الحزمة صراحة
                notifyUI(context, packageName, true)
            }

            // 🟡 الحالة الثانية: النظام يحتاج إلى تأكيد المستخدم للتثبيت
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i("InstallReceiver", "Requesting user confirmation for install...")
                
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                } else {
                    Toast.makeText(context, "خطأ: لم يتم العثور على واجهة التثبيت", Toast.LENGTH_SHORT).show()
                }
            }

            // 🔴 الحالة الثالثة: فشل التثبيت
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val userFriendlyMessage = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "تم إلغاء التثبيت بواسطة المستخدم."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "لا توجد مساحة كافية في الجهاز."
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "يوجد إصدار متعارض مثبت مسبقاً! يرجى حذفه أولاً."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "التطبيق غير متوافق مع جهازك."
                    else -> "فشل التثبيت: $message"
                }

                Toast.makeText(context, userFriendlyMessage, Toast.LENGTH_LONG).show()
                Log.e("InstallReceiver", "Install failed for $packageName: $userFriendlyMessage")
                
                notifyUI(context, packageName, false)
            }

            else -> {
                Log.w("InstallReceiver", "Unknown status: $status, Message: $message")
            }
        }
    }

    /**
     * دالة مساعدة لإرسال تحديث لواجهة المستخدم مع تحديد الحزمة بصراحة (Explicit Intent)
     * لتجاوز قيود الأمان في أندرويد 13 و 14+
     */
    private fun notifyUI(context: Context, packageName: String, isSuccess: Boolean) {
        val updateIntent = Intent("com.fix.engine.abdullah.UPDATE_UI").apply {
            setPackage(context.packageName) // 🔒 تعديل جوهري لمنع حظر الإشعار في أندرويد 14+
            putExtra("PACKAGE_NAME", packageName)
            putExtra("IS_SUCCESS", isSuccess)
        }
        context.sendBroadcast(updateIntent)
    }
}
