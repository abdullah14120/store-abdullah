package com.fix.engine.abdullah.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Smart Install Status Receiver with Automatic Fallback (Plan C)
 */
class InstallStatusReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra("PACK_NAME") ?: "التطبيق"
        val apkPath = intent.getStringExtra("APK_PATH")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "تم تثبيت $packageName بنجاح! 🎉", Toast.LENGTH_SHORT).show()
            }
            
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // إذا كان النظام يحتاج تأكيد يدوي طبيعي (مثل أندرويد 11 أو تثبيت جديد لأول مرة)
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }
            
            else -> {
                // 🚨 الخطة ج (Fallback): الجلسة فشلت بسبب قيود نظام التشغيل أو الواجهات (شاومي، هواوي... إلخ)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                
                if (!apkPath.isNullOrEmpty()) {
                    val file = File(apkPath)
                    if (file.exists()) {
                        // إشعار لوحي سريع للمستخدم لبدء نظام الإنقاذ التلقائي
                        Toast.makeText(context, "تم تشغيل مثبت الإنقاذ لـ $packageName 🛠️", Toast.LENGTH_LONG).show()
                        
                        // فتح نافذة التثبيت التقليدية للنظام فوراً لتجاوز قيود الواجهة
                        launchLegacyInstaller(context, file)
                    } else {
                        Toast.makeText(context, "فشل التثبيت الذكي وملف الـ APK غير موجود: $message", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "فشل التثبيت الذكي: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * كود الإنقاذ التقليدي لفتح نافذة النظام الافتراضية عبر الـ FileProvider المعتمد في متجرك
     */
    private fun launchLegacyInstaller(context: Context, file: File) {
        try {
            // جلب الـ authorities ديناميكياً بناءً على حزمة المشروع الحالية لتفادي أي تضارب
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
            Toast.makeText(context, "عذراً، فشل مثبت الإنقاذ أيضاً: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
