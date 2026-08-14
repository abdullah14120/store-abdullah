package com.fix.engine.abdullah.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fix.engine.abdullah.MainActivity
import com.fix.engine.abdullah.R

object NotificationEngine {
    private const val CHANNEL_ID = "store_updates_channel"

    fun sendUpdateNotification(context: Context, updatesCount: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "تحديثات المتجر", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "تنبيهات تلقائية عند توفر تحديثات جديدة"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("تحديثات متوفرة لـ تطبيقاتك! 🚀")
            .setContentText("يوجد عدد ($updatesCount) من تطبيقاتك تمتلك إصدارات محدثة، قم بتثبيتها الآن.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}
