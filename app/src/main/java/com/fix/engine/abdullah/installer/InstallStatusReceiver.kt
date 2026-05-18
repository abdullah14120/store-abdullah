package com.fix.engine.abdullah.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra("PACK_NAME") ?: "التطبيق"

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "تم تثبيت $packageName بنجاح! 🎉", Toast.LENGTH_SHORT).show()
                // هنا مستقبلاً سنقوم ببث حدث لتحديث الواجهة تلقائياً
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // إذا كان النظام يحتاج تأكيد من المستخدم (مثل أندرويد 11 أو تثبيت جديد لأول مرة)
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "فشل تثبيت التحديث: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
