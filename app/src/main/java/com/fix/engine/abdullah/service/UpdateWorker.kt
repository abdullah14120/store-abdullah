package com.fix.engine.abdullah.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fix.engine.abdullah.MainActivity
import com.fix.engine.abdullah.R

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Background Update Checker
 * Feature: Smart Notifications & Material 3 Theme Integration (M3 Olive Style)
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. هنا تقوم بجلب ملف الـ JSON الخاص بك وفحص الإصدارات
            val isUpdateAvailable = checkForUpdatesInJson()

            // 2. إرسال الإشعار فقط إذا كان هناك تحديث فعلي
            if (isUpdateAvailable) {
                sendNotification(
                    "تحديثات جديدة متوفرة لـ تطبيقاتك! 🚀", 
                    "هناك إصدارات جديدة ومحدثة بانتظارك الآن داخل متجر Abdullah، تفقدها وثبتها فوراً!"
                )
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // في حال فشل الاتصال بالإنترنت أو فشل قراءة JSON
            Result.retry() 
        }
    }

    // دالة افتراضية لمحاكاة فحص التحديثات (قم بتعديلها بمنطقك الخاص)
    private suspend fun checkForUpdatesInJson(): Boolean {
        // TODO: تنفيذ منطق جلب الـ JSON ومقارنة أرقام الإصدارات
        return true 
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "abdullah_store_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "تحديثات متجر Abdullah", 
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات تلقائية عند توفر تحديثات جديدة للتطبيقات"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            flags
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            // 🟢 التعديل الجوهري: استخدام أيقونة شفافة (Vector) لمنع المربع الرمادي
            .setSmallIcon(R.drawable.ic_notification_transparent) 
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(applicationContext, R.color.md_theme_d_primary))
            .build()

        notificationManager.notify(1002, notification) 
    }
}
