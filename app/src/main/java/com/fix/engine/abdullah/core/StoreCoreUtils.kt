package com.fix.engine.abdullah.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.fix.engine.abdullah.R
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileInputStream

object StorageManagerCore {
    fun hasEnoughStorage(appSizeInBytes: Long = 0L): Boolean {
        val usableSpace = Environment.getDataDirectory().usableSpace
        val minimumRequired = if (appSizeInBytes > 0L) {
            (appSizeInBytes * 2) + (100 * 1024 * 1024L)
        } else {
            300 * 1024 * 1024L
        }
        return usableSpace >= minimumRequired
    }

    fun cleanupAppFiles(context: Context, fileName: String): Boolean {
        val downloadFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)
        val tempFile = File(downloadFolder, "$fileName.tmp")

        var isDeleted = false
        if (finalFile.exists() && finalFile.delete()) isDeleted = true
        if (tempFile.exists() && tempFile.delete()) isDeleted = true
        return isDeleted
    }
}

object PackageInstallerCore {
    init {
        System.loadLibrary("native-lib")
    }

    external fun getSecureFirebaseUrl(): String

    fun createInstallSession(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false

        val packageManager = context.packageManager
        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        FileInputStream(file).use { inputStream ->
            session.openWrite("StoreInstallSession", 0, file.length()).use { outputStream ->
                inputStream.copyTo(outputStream)
                session.fsync(outputStream)
            }
        }

        val intent = Intent(context, com.fix.engine.abdullah.installer.InstallStatusReceiver::class.java).apply {
            action = "com.fix.engine.abdullah.COMMIT_INSTALL"
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            pendingIntentFlags
        )

        session.commit(pendingIntent.intentSender)
        session.close()
        return true
    }
}

object AppNotificationCore {
    private const val INSTALL_CHANNEL_ID = "INSTALL_CHANNEL"

    fun showReadyToInstallNotification(context: Context, appName: String, file: File) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(INSTALL_CHANNEL_ID, "تثبيت التطبيقات", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            appName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("اكتمل تنزيل $appName")
            .setContentText("اضغط هنا للبدء في التثبيت")
            .setColor(Color.parseColor("#4CAF50"))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(appName.hashCode(), notification)
    }
}

object ShareManagerCore {
    fun getShareIntent(context: Context, file: File, appName: String): Intent? {
        if (!file.exists()) return null
        return try {
            val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }
}
