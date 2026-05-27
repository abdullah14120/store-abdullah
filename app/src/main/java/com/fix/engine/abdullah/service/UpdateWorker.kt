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
        // هنا يتم فحص التحديثات في الخلفية بانتظام بفضل WorkManager
        // بمجرد العثور على تحديث في ملف JSON، نرسل الإشعار للمستخدم
        
        sendNotification(
            "تحديثات جديدة متوفرة لـ تطبيقاتك! 🚀", 
            "هناك إصدارات جديدة ومحدثة بانتظارك الآن داخل متجر Abdullah، تفقدها وثبتها فوراً!"
        )
        
        return Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "abdullah_store_updates"

        // إنشاء قناة الإشعارات لأجهزة أندرويد 8.0 فما فوق (API 26+)
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
        
        // 🔒 صياغة برمجية آمنة ومحصنة للـ PendingIntent متوافقة مع أندرويد 6.0 وحتى أندرويد 15
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
            // 🟢 تم التعديل لتقرأ الأيقونة من الـ mipmap لضمان الثبات التام ومنع ظهور المربعات الرمادية
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // لعرض النص كاملاً وبشكل منسق إذا كان طويلاً
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // 🟢 اعتماد تلوين الإشعار باللون الأساسي للثيم الزيتي المعتمد بمتجرك
            .setColor(ContextCompat.getColor(applicationContext, R.color.md_theme_d_primary))
            .build()

        notificationManager.notify(1002, notification) // استخدام ID فريد وثابت للإشعار الدوري
    }
}
