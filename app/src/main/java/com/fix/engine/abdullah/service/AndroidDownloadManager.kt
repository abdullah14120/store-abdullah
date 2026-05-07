package com.fix.engine.abdullah.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

class AndroidDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(url: String, fileName: String): Long {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("FIX ENGINE")
            .setDescription("جاري تحميل $fileName...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true) // السماح بالتحميل عبر بيانات الهاتف
            .setAllowedOverRoaming(true)

        // إرجاع معرف التحميل (Download ID) لمتابعته لاحقاً
        return downloadManager.enqueue(request)
    }
}
