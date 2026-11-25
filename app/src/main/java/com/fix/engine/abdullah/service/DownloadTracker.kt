package com.fix.engine.abdullah.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Live StateFlow Progress Tracker
 */
class DownloadTracker(private val context: Context) {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var trackingJob: Job? = null

    fun startTracking(downloadId: Int, onProgress: (Int, String) -> Unit) {
        stopTracking()

        if (downloadId == -1) {
            onProgress(-1, "فشل في بدء التحميل")
            return
        }

        trackingJob = uiScope.launch {
            StoreDownloadEngine.downloadsState.collect { tasks ->
                val task = tasks[downloadId] ?: return@collect
                when (task.status) {
                    StoreDownloadStatus.QUEUED,
                    StoreDownloadStatus.RESOLVING -> {
                        onProgress(task.progress.coerceIn(0, 99), task.statusMessage)
                    }
                    StoreDownloadStatus.DOWNLOADING -> {
                        onProgress(task.progress.coerceIn(0, 99), task.statusMessage)
                    }
                    StoreDownloadStatus.PAUSED_NETWORK -> {
                        onProgress(task.progress.coerceIn(0, 99), task.statusMessage)
                    }
                    StoreDownloadStatus.COMPLETED -> {
                        stopTracking()
                        onProgress(100, task.statusMessage)
                    }
                    StoreDownloadStatus.FAILED -> {
                        stopTracking()
                        onProgress(-1, task.statusMessage)
                    }
                    StoreDownloadStatus.CANCELLED -> {
                        stopTracking()
                    }
                }
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }
}
