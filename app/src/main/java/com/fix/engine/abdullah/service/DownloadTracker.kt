package com.fix.engine.abdullah.service

import android.content.Context
import com.fix.engine.abdullah.data.network.LinkExtractor
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Resilient Progress Tracker (gdown Exponential Backoff & HTML Guard)
 */
class DownloadTracker(private val context: Context) {

    companion object {
        private const val MAX_SMART_RETRIES = 4
    }

    private var currentListener: AbstractFetchListener? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var retryJob: Job? = null
    private var smartRetryAttempt = 0

    fun startTracking(downloadId: Int, onProgress: (Int, String) -> Unit) {
        stopTracking()
        smartRetryAttempt = 0

        if (downloadId == -1) {
            onProgress(-1, "فشل في بدء التحميل")
            return
        }

        val fetch = Fetch.getDefaultInstance()

        currentListener = object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (download.id == downloadId) {
                    // إعادة تصفير عداد المحاولات بمجرد استئناف تدفق البيانات بنجاح
                    if (downloadedBytesPerSecond > 0L) {
                        smartRetryAttempt = 0
                    }

                    val progress = download.progress.coerceIn(0, 99)
                    val downloadedMb = download.downloaded / (1024.0 * 1024.0)
                    val totalMb = download.total / (1024.0 * 1024.0)

                    val speedKB = downloadedBytesPerSecond / 1024.0
                    val speedText = if (speedKB >= 1024.0) {
                        String.format(Locale.ENGLISH, "%.1f MB/s", speedKB / 1024.0)
                    } else {
                        String.format(Locale.ENGLISH, "%.1f KB/s", speedKB)
                    }

                    val sizeText = if (download.total > 0) {
                        String.format(Locale.ENGLISH, "%s • %.1f/%.1f MB", speedText, downloadedMb, totalMb)
                    } else {
                        String.format(Locale.ENGLISH, "%s • %.1f MB", speedText, downloadedMb)
                    }

                    uiScope.launch { onProgress(progress, sizeText) }
                }
            }

            override fun onCompleted(download: Download) {
                if (download.id == downloadId) {
                    retryJob?.cancel()
                    val downloadedFile = File(download.file)

                    // 🛡️ فحص ذكي (مستوحى من gdown): التأكد أن الملف المحمل ليس صفحة HTML لتحذير أو حصة Google Drive
                    if (isHtmlErrorPage(downloadedFile)) {
                        downloadedFile.delete()
                        fetch.remove(download.id)
                        stopTracking()
                        val gdriveHint = LinkExtractor.lastErrorMessage
                        val errorMsg = if (!gdriveHint.isNullOrBlank()) {
                            "تعذر التحميل من Google Drive: $gdriveHint"
                        } else {
                            "تجاوز رابط Google Drive حد التحميل المؤقت أو يتطلب صلاحية، يرجى المحاولة لاحقاً."
                        }
                        uiScope.launch { onProgress(-1, errorMsg) }
                        return
                    }

                    stopTracking()
                    uiScope.launch { onProgress(100, "اكتمل التحميل بنجاح ✨") }
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == downloadId) {
                    // 🛡️ خوارزمية gdown: عند تقطع الإنترنت الضعيف لا نحذف الجزء المحمّل، بل نعيد الاستئناف بتدرج زمني
                    if (isTransientNetworkError(error) && smartRetryAttempt < MAX_SMART_RETRIES) {
                        smartRetryAttempt++
                        val attemptNum = smartRetryAttempt
                        val maxBackoffSec = min(1 shl attemptNum, 16)
                        val delayMs = Random.nextLong(1500L, (maxBackoffSec * 1000L).coerceAtLeast(2000L))
                        val delaySec = String.format(Locale.ENGLISH, "%.0f", delayMs / 1000.0)
                        val currentProgress = download.progress.coerceIn(0, 99)

                        retryJob?.cancel()
                        retryJob = uiScope.launch {
                            onProgress(
                                currentProgress,
                                "تقطع الاتصال... استئناف تلقائي ($attemptNum/$MAX_SMART_RETRIES) خلال ${delaySec}ث 🔄"
                            )
                            delay(delayMs)
                            try {
                                fetch.retry(download.id)
                            } catch (_: Exception) {
                                handlePermanentFailure(fetch, download, error, onProgress)
                            }
                        }
                        return
                    }

                    handlePermanentFailure(fetch, download, error, onProgress)
                }
            }

            override fun onCancelled(download: Download) {
                if (download.id == downloadId) {
                    retryJob?.cancel()
                    File(download.file).delete()
                    fetch.remove(download.id)
                    stopTracking()
                }
            }
        }

        fetch.addListener(currentListener!!)
    }

    private fun handlePermanentFailure(
        fetch: Fetch,
        download: Download,
        error: Error,
        onProgress: (Int, String) -> Unit
    ) {
        retryJob?.cancel()
        File(download.file).delete()
        fetch.remove(download.id)
        stopTracking()

        val errorMsg = when {
            error.name.contains("STORAGE", ignoreCase = true) ->
                "لا توجد مساحة تخزين كافية على جهازك لإكمال التنزيل."
            error.name.contains("NOT_FOUND", ignoreCase = true) ->
                "الملف غير متوفر حالياً على الخادم (404)."
            !LinkExtractor.lastErrorMessage.isNullOrBlank() ->
                "تعذر التحميل من Google Drive: ${LinkExtractor.lastErrorMessage}"
            else ->
                "انقطع التحميل بسبب ضعف الشبكة، يرجى التحقق من الإنترنت والمحاولة مجدداً."
        }
        uiScope.launch { onProgress(-1, errorMsg) }
    }

    /**
     * التمييز بين أخطاء الشبكة المؤقتة (القابلة للاستئناف) والأخطاء الدائمة (مثل امتلاء التخزين أو 404)
     */
    private fun isTransientNetworkError(error: Error): Boolean {
        val name = error.name.uppercase(Locale.US)
        if (name.contains("STORAGE") ||
            name.contains("NOT_FOUND") ||
            name.contains("FILE_NOT_CREATED") ||
            name.contains("FILE_ALLOCATION") ||
            name.contains("DOES_NOT_EXIST")
        ) {
            return false
        }
        return true
    }

    /**
     * فحص أول 512 بايت من الملف إذا كان صغيراً للتأكد أنه ليس صفحة HTML خطأ من Google Drive
     */
    private fun isHtmlErrorPage(file: File): Boolean {
        if (!file.exists() || file.length() > 256 * 1024L) return false
        return try {
            val buffer = ByteArray(512)
            val bytesRead = FileInputStream(file).use { it.read(buffer) }
            if (bytesRead <= 0) return true
            val header = String(buffer, 0, bytesRead, Charsets.UTF_8).trimStart().lowercase(Locale.US)
            header.startsWith("<!doctype html") ||
                header.startsWith("<html") ||
                header.contains("<title>google drive") ||
                header.contains("uc-error-subcaption")
        } catch (_: Exception) {
            false
        }
    }

    fun stopTracking() {
        retryJob?.cancel()
        retryJob = null
        currentListener?.let {
            try {
                Fetch.getDefaultInstance().removeListener(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentListener = null
        }
    }
}
