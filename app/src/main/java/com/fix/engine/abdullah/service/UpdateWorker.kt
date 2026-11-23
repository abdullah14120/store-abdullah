package com.fix.engine.abdullah.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * Feature: Smart Notifications, Battery Optimization & M3 Theme Integration
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        // 🛡️ تحميل مكتبة الحماية للوصول إلى الرابط المشفر في الخلفية
        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun getSecureRepoUrl(): String

    override suspend fun doWork(): Result {
        // 🛡️ 1. التحقق من صلاحية الإشعارات أولاً (لتوفير طاقة البطارية والبيانات)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // إذا لم تكن الصلاحية ممنوحة، لا داعي لاستهلاك الإنترنت والبحث عن تحديثات
                return Result.success()
            }
        }

        return try {
            // 2. جلب الرابط الآمن من مكتبة C++
            val secureUrl = getSecureRepoUrl()

            // 3. جلب الـ JSON وفحص الإصدارات (قم بتمرير الرابط للدالة الخاصة بك)
            val isUpdateAvailable = checkForUpdatesInJson(secureUrl)

            // 4. إرسال الإشعار
            if (isUpdateAvailable) {
                sendNotification(
                    "تحديثات جديدة متوفرة لـ تطبيقاتك! 🚀", 
                    "هناك إصدارات جديدة ومحدثة بانتظارك الآن داخل متجر Abdullah، تفقدها وثبتها فوراً!"
                )
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // إعادة المحاولة في حال فشل الشبكة
            Result.retry() 
        }
    }

    // TODO: دمج منطق الـ Repository الخاص بك هنا لتحميل الـ JSON عبر Retrofit أو Fetch
    private suspend fun checkForUpdatesInJson(url: String): Boolean {
        // هنا ستقوم بمقارنة versionCode للتطبيقات المثبتة مع القادمة من url
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
