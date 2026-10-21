package com.fix.engine.abdullah.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.fix.engine.abdullah.ui.details.AppDetailsActivity

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah (Store Engine)
 * Feature: Smart Foreground Confirmation Routing & Detailed Error Reporting
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.fix.engine.abdullah.COMMIT_INSTALL") {
            return
        }

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""

        when (status) {
            // 🟢 1. نجاح التثبيت
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "تم تثبيت التطبيق بنجاح! ✨", Toast.LENGTH_SHORT).show()
                notifyUI(
                    context = context,
                    packageName = packageName,
                    isSuccess = true,
                    statusCode = status,
                    errorMessage = null,
                    confirmationIntent = null
                )
            }

            // 🟡 2. النظام يطلب موافقة المستخدم على التثبيت
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmationIntent != null) {
                    if (AppDetailsActivity.isActivityVisible) {
                        // 🚀 إرسال النافذة إلى الشاشة النشطة لتجاوز حظر الخلفية في شاومي وأندرويد 14+
                        notifyUI(
                            context = context,
                            packageName = packageName,
                            isSuccess = false,
                            statusCode = status,
                            errorMessage = null,
                            confirmationIntent = confirmationIntent
                        )
                    } else {
                        try {
                            confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(confirmationIntent)
                        } catch (e: Exception) {
                            Log.e("InstallReceiver", "Failed to start confirmation activity from background", e)
                        }
                    }
                } else {
                    notifyUI(
                        context = context,
                        packageName = packageName,
                        isSuccess = false,
                        statusCode = PackageInstaller.STATUS_FAILURE,
                        errorMessage = "لم يتم العثور على نافذة التثبيت في النظام.",
                        confirmationIntent = null
                    )
                }
            }

            // 🔴 3. فشل التثبيت (مع تحديد السبب الدقيق للمستخدم)
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {

                val detailedMessage = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED ->
                        "تم إلغاء عملية التثبيت من قِبل المستخدم."
                    PackageInstaller.STATUS_FAILURE_STORAGE ->
                        "لا توجد مساحة تخزين كافية في جهازك لإتمام تثبيت التطبيق. يرجى تفريغ بعض المساحة ثم المحاولة مجدداً."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "يوجد إصدار مثبت مسبقاً من هذا التطبيق بتوقيع مختلف (مثلاً من مصدر آخر). يجب حذف النسخة الحالية أولاً لتتمكن من تثبيت هذه النسخة."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "هذا التطبيق غير متوافق مع معالج جهازك أو مع إصدار نظام الأندرويد لديك."
                    PackageInstaller.STATUS_FAILURE_INVALID ->
                        "ملف التثبيت (APK) غير صالح أو تالف. يرجى مسح ملف التثبيت وإعادة تنزيله."
                    PackageInstaller.STATUS_FAILURE_BLOCKED ->
                        "تم حظر التثبيت بواسطة نظام الحماية في جهازك أو Google Play Protect. يمكنك المحاولة عبر وضع التثبيت المباشر."
                    else ->
                        "تعذر إتمام التثبيت تلقائياً (${rawMessage.ifBlank { "قيود من نظام الجهاز" }}). يمكنك التثبيت فوراً عبر الوضع المباشر."
                }

                Log.e("InstallReceiver", "Install failed ($status): $detailedMessage")

                if (!AppDetailsActivity.isActivityVisible) {
                    Toast.makeText(context, detailedMessage, Toast.LENGTH_LONG).show()
                }

                notifyUI(
                    context = context,
                    packageName = packageName,
                    isSuccess = false,
                    statusCode = status,
                    errorMessage = detailedMessage,
                    confirmationIntent = null
                )
            }
        }
    }

    private fun notifyUI(
        context: Context,
        packageName: String,
        isSuccess: Boolean,
        statusCode: Int,
        errorMessage: String?,
        confirmationIntent: Intent?
    ) {
        val updateIntent = Intent("com.fix.engine.abdullah.UPDATE_UI").apply {
            setPackage(context.packageName)
            putExtra("PACKAGE_NAME", packageName)
            putExtra("IS_SUCCESS", isSuccess)
            putExtra("STATUS_CODE", statusCode)
            putExtra("ERROR_MESSAGE", errorMessage)
            if (confirmationIntent != null) {
                putExtra("CONFIRMATION_INTENT", confirmationIntent)
            }
        }
        context.sendBroadcast(updateIntent)
    }
}
