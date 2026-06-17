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
 * Feature: Advanced PackageInstaller Status Receiver
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
                
                // 💡 تلميح هندسي: هنا يمكنك إرسال إشعار محلي (Broadcast) للواجهة 
                // لكي تقوم بتغيير الزر من "جاري التثبيت" إلى "فتح"
                notifyUI(context, packageName, true)
            }

            // 🟡 الحالة الثانية (الأهم): النظام يحتاج إلى تأكيد المستخدم للتثبيت
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i("InstallReceiver", "Requesting user confirmation for install...")
                
                // استخراج الـ Intent الخاص بنافذة تأكيد التثبيت للنظام
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmationIntent != null) {
                    // 🚀 شرط أساسي: يجب إضافة هذا الـ Flag لأننا نطلق الواجهة من Receiver (خارج الـ Activity)
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                } else {
                    Toast.makeText(context, "خطأ: لم يتم العثور على واجهة التثبيت", Toast.LENGTH_SHORT).show()
                }
            }

            // 🔴 الحالة الثالثة: فشل التثبيت لأسباب متعددة (رفض المستخدم، مساحة غير كافية، تضارب حزم...)
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                // معالجة الأخطاء الشائعة برسائل واضحة للمستخدم
                val userFriendlyMessage = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "تم إلغاء التثبيت بواسطة المستخدم."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "لا توجد مساحة كافية في الجهاز."
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "يوجد إصدار متعارض مثبت مسبقاً! يرجى حذفه أولاً."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "التطبيق غير متوافق مع جهازك."
                    else -> "فشل التثبيت: $message"
                }

                Toast.makeText(context, userFriendlyMessage, Toast.LENGTH_LONG).show()
                Log.e("InstallReceiver", "Install failed for $packageName: $userFriendlyMessage")
                
                // إبلاغ الواجهة بالفشل لإعادة الزر إلى "تثبيت"
                notifyUI(context, packageName, false)
            }

            else -> {
                Log.w("InstallReceiver", "Unknown status: $status, Message: $message")
            }
        }
    }

    /**
     * دالة مساعدة لإرسال تحديث لواجهة المستخدم (MainActivity أو غيرها)
     */
    private fun notifyUI(context: Context, packageName: String, isSuccess: Boolean) {
        val updateIntent = Intent("com.fix.engine.abdullah.UPDATE_UI")
        updateIntent.putExtra("PACKAGE_NAME", packageName)
        updateIntent.putExtra("IS_SUCCESS", isSuccess)
        
        // إرسال Broadcast داخلي يمكن لـ MainActivity التقاطه
        context.sendBroadcast(updateIntent)
    }
}
