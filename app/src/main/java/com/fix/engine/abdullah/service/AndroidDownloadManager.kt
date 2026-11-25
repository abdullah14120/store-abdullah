package com.fix.engine.abdullah.service

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Lightweight Bridge to StoreDownloadEngine (Zero Fetch2 Overhead)
 */
class AndroidDownloadManager(private val context: Context) {

    fun enqueueDownload(
        url: String,
        fileName: String,
        packageName: String,
        displayName: String = packageName,
        expectedSize: Long = 0L
    ): Int {
        return try {
            StoreDownloadEngine.init(context.applicationContext)
            StoreDownloadEngine.enqueue(
                originalUrl = url,
                fileName = fileName,
                packageName = packageName,
                displayName = displayName,
                expectedSize = expectedSize
            )
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            -1
        }
    }

    fun cancelDownload(downloadId: Int, deletePartial: Boolean = true) {
        if (downloadId != -1) {
            StoreDownloadEngine.cancel(downloadId, deletePartial = deletePartial)
        }
    }
}
