package com.fix.engine.abdullah.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import com.fix.engine.abdullah.data.network.LinkExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

enum class StoreDownloadStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PAUSED_NETWORK,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class StoreDownloadTask(
    val id: Int,
    val originalUrl: String,
    val fileName: String,
    val packageName: String,
    val displayName: String,
    val expectedSize: Long,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = expectedSize,
    val speedBytesPerSec: Long = 0L,
    val progress: Int = 0,
    val status: StoreDownloadStatus = StoreDownloadStatus.QUEUED,
    val statusMessage: String = "جاري تجهيز التنزيل..."
)

/**
 * Developed by: Abdullah Al-Tamimi
 * Unified Resilient Download Engine combining:
 * - gdown: Single-session Google Drive resolution, Cookies, Identity encoding, Range + If-Range & HTML Guard
 * - Komi Store: .part + .part.meta identity ownership, strict RangeRequest decisions, per-file Mutex & ProgressThrottle
 * - Orion Store: 3x HTTP/1.1 parallel split acceleration + 15s Stall Watchdog & callTimeout=0
 * - SealPlus: NET_CAPABILITY_VALIDATED auto-pause/auto-resume & PARTIAL_WAKE_LOCK
 */
object StoreDownloadEngine {

    private const val SPLIT_PARTS = 3
    private const val MIN_SPLIT_SIZE_BYTES = 4L * 1024L * 1024L
    private const val STALL_TIMEOUT_TICKS = 30 // 30 * 500ms = 15s
    private const val STREAM_BUFFER_SIZE = 64 * 1024
    private const val MAX_TRANSIENT_RETRIES = 4
    private const val UI_THROTTLE_MS = 180L
    private const val PART_SUFFIX = ".part"
    private const val META_SUFFIX = ".part.meta"
    private const val SPLIT_SUFFIX = ".parts"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val fileLocks = ConcurrentHashMap<String, Mutex>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<Int>()

    private val _downloadsState = MutableStateFlow<Map<Int, StoreDownloadTask>>(emptyMap())
    val downloadsState: StateFlow<Map<Int, StoreDownloadTask>> = _downloadsState.asStateFlow()

    @Volatile
    private lateinit var appContext: Context

    @Volatile
    private var isInitialized = false

    private var wakeLock: PowerManager.WakeLock? = null
    private val networkCallbackLock = Any()
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private data class PartialMeta(
        val packageName: String,
        val expectedSize: Long,
        val validator: String?
    ) {
        fun serialize(): String = buildString {
            append("pkg=").append(packageName).append('\n')
            append("size=").append(expectedSize).append('\n')
            append("validator=").append(validator.orEmpty()).append('\n')
        }

        companion object {
            fun parse(file: File): PartialMeta? {
                if (!file.exists()) return null
                return runCatching {
                    var pkg = ""
                    var size = 0L
                    var validator: String? = null
                    file.forEachLine(Charsets.UTF_8) { raw ->
                        val idx = raw.indexOf('=')
                        if (idx > 0) {
                            val k = raw.substring(0, idx).trim()
                            val v = raw.substring(idx + 1).trim()
                            when (k) {
                                "pkg" -> pkg = v
                                "size" -> size = v.toLongOrNull() ?: 0L
                                "validator" -> validator = v.takeIf { it.isNotBlank() }
                            }
                        }
                    }
                    if (pkg.isBlank()) null else PartialMeta(pkg, size, validator)
                }.getOrNull()
            }
        }
    }

    private data class ContentRangeInfo(val start: Long?, val total: Long?) {
        companion object {
            fun parse(header: String?): ContentRangeInfo? {
                val body = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                if (!body.substringBefore(' ', "").equals("bytes", ignoreCase = true)) return null
                val spec = body.substringAfter(' ', "").trim()
                val total = spec.substringAfterLast('/', "").trim().toLongOrNull()
                val rangePart = spec.substringBefore('/').trim()
                val start = if (rangePart.startsWith("*")) null else rangePart.substringBefore('-').trim().toLongOrNull()
                return if (start == null && total == null) null else ContentRangeInfo(start, total)
            }
        }
    }

    private enum class RangeDecision { APPEND, TRUNCATE_REWRITE, RESTART, UNEXPECTED }

    private fun decideRange(statusCode: Int, resumeFrom: Long, range: ContentRangeInfo?): RangeDecision {
        return when (statusCode) {
            206 -> if (range?.start == resumeFrom) RangeDecision.APPEND else RangeDecision.RESTART
            416 -> RangeDecision.RESTART
            200 -> if (resumeFrom > 0L) RangeDecision.TRUNCATE_REWRITE else RangeDecision.APPEND
            else -> RangeDecision.UNEXPECTED
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
        }
    }

    fun downloadIdFor(fileName: String): Int = fileName.hashCode() and 0x7FFFFFFF

    fun downloadsDir(): File {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPartialProgressPercent(fileName: String, packageName: String, expectedSize: Long): Int {
        if (!isInitialized) return 0
        val dir = downloadsDir()
        val partFile = File(dir, "$fileName$PART_SUFFIX")
        val metaFile = File(dir, "$fileName$META_SUFFIX")
        if (!partFile.exists() || partFile.length() <= 0L) return 0
        val meta = PartialMeta.parse(metaFile) ?: return 0
        if (meta.packageName != packageName) return 0
        val total = if (meta.expectedSize > 0L) meta.expectedSize else expectedSize
        if (total <= 0L) return 0
        return ((partFile.length() * 100L) / total).toInt().coerceIn(1, 99)
    }

    fun getTaskById(id: Int): StoreDownloadTask? = _downloadsState.value[id]

    fun getTaskByFileName(fileName: String): StoreDownloadTask? =
        _downloadsState.value[downloadIdFor(fileName)]

    fun hasActiveDownloads(): Boolean =
        _downloadsState.value.values.any {
            it.status == StoreDownloadStatus.QUEUED ||
                it.status == StoreDownloadStatus.RESOLVING ||
                it.status == StoreDownloadStatus.DOWNLOADING ||
                it.status == StoreDownloadStatus.PAUSED_NETWORK
        }

    fun enqueue(
        originalUrl: String,
        fileName: String,
        packageName: String,
        displayName: String = packageName,
        expectedSize: Long = 0L
    ): Int {
        val id = downloadIdFor(fileName)
        val existing = _downloadsState.value[id]
        if (existing != null && jobs[id]?.isActive == true &&
            (existing.status == StoreDownloadStatus.RESOLVING || existing.status == StoreDownloadStatus.DOWNLOADING)
        ) {
            return id
        }

        cancelledIds.remove(id)
        val initialPartial = getPartialProgressPercent(fileName, packageName, expectedSize)
        val partBytes = File(downloadsDir(), "$fileName$PART_SUFFIX").takeIf { it.exists() && initialPartial > 0 }?.length() ?: 0L

        val task = StoreDownloadTask(
            id = id,
            originalUrl = originalUrl,
            fileName = fileName,
            packageName = packageName,
            displayName = displayName,
            expectedSize = expectedSize,
            downloadedBytes = partBytes,
            totalBytes = expectedSize,
            progress = initialPartial,
            status = StoreDownloadStatus.RESOLVING,
            statusMessage = if (initialPartial > 0) "جاري استئناف التنزيل من $initialPartial%..." else "جاري تجهيز رابط التحميل..."
        )
        updateTask(task)
        registerNetworkAutoResumeMonitor()
        acquireWakeLock()
        StoreDownloadForegroundService.sync(appContext)

        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            executeTask(task)
        }
        return id
    }

    fun cancel(downloadId: Int, deletePartial: Boolean = true) {
        cancelledIds.add(downloadId)
        val task = _downloadsState.value[downloadId]
        val fileName = task?.fileName
        if (fileName != null) {
            cancelCallsFor(fileName)
        }
        jobs.remove(downloadId)?.cancel()

        if (fileName != null && deletePartial) {
            deleteWorkFiles(fileName, deleteFinal = true)
        }

        _downloadsState.update { current ->
            val target = current[downloadId] ?: return@update current
            current + (downloadId to target.copy(
                status = StoreDownloadStatus.CANCELLED,
                speedBytesPerSec = 0L,
                statusMessage = "تم إلغاء التنزيل"
            ))
        }
        cleanupIdleResources()
    }

    fun cancelByFileName(fileName: String, deletePartial: Boolean = true) {
        cancel(downloadIdFor(fileName), deletePartial)
    }

    fun deleteWorkFiles(fileName: String, deleteFinal: Boolean = false) {
        val dir = downloadsDir()
        File(dir, "$fileName$PART_SUFFIX").delete()
        File(dir, "$fileName$META_SUFFIX").delete()
        File(dir, "$fileName$SPLIT_SUFFIX").delete()
        File(dir, "$fileName.tmp").delete()
        if (deleteFinal) {
            File(dir, fileName).delete()
        }
    }

    private suspend fun executeTask(initialTask: StoreDownloadTask) {
        val id = initialTask.id
        val fileName = initialTask.fileName
        val lock = fileLocks.getOrPut(fileName) { Mutex() }

        lock.withLock {
            val dir = downloadsDir()
            val finalFile = File(dir, fileName)
            val partFile = File(dir, "$fileName$PART_SUFFIX")
            val metaFile = File(dir, "$fileName$META_SUFFIX")
            val splitFile = File(dir, "$fileName$SPLIT_SUFFIX")
            File(dir, "$fileName.tmp").delete()

            try {
                // 1. استخراج الرابط المباشر الصالح + الكوكيز عبر LinkExtractor (gdown)
                var directUrl = LinkExtractor.extract(initialTask.originalUrl)
                var attempt = 0
                var splitTried = false

                while (attempt <= MAX_TRANSIENT_RETRIES && id !in cancelledIds && scope.isActive) {
                    // التحقق من ملكية الملف الجزئي (Komi Store PartialOwnership)
                    val storedMeta = PartialMeta.parse(metaFile)
                    val canReusePart = partFile.exists() && partFile.length() > 0L &&
                        storedMeta != null && storedMeta.packageName == initialTask.packageName

                    if (partFile.exists() && !canReusePart) {
                        partFile.delete()
                        metaFile.delete()
                    }

                    val existingBytes = if (canReusePart) partFile.length() else 0L

                    // 2. إذا بدأنا من الصفر (existingBytes == 0) ولم نجرب التقسيم بعد، نحاول التسريع بـ 3 اتصالات HTTP/1.1 (Orion Store)
                    if (existingBytes == 0L && !splitTried) {
                        splitTried = true
                        val splitSuccess = tryParallelSplitDownload(
                            task = initialTask,
                            directUrl = directUrl,
                            splitFile = splitFile,
                            finalFile = finalFile
                        )
                        if (id in cancelledIds) return@withLock
                        if (splitSuccess) {
                            markCompleted(id, finalFile)
                            return@withLock
                        }
                        splitFile.delete()
                    }

                    // 3. التحميل الأحادي المستقر القابل للاستئناف الدقيق بـ Range + If-Range (.part + .part.meta)
                    val streamOutcome = runCatching {
                        runResumableSingleStream(
                            task = initialTask,
                            directUrl = directUrl,
                            partFile = partFile,
                            metaFile = metaFile,
                            finalFile = finalFile,
                            storedMeta = storedMeta
                        )
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        StreamOutcome.TransientError(error.localizedMessage ?: "انقطع الاتصال")
                    }

                    if (id in cancelledIds) return@withLock

                    when (streamOutcome) {
                        is StreamOutcome.Completed -> {
                            markCompleted(id, finalFile)
                            return@withLock
                        }
                        is StreamOutcome.RefreshUrlAndResume -> {
                            // تجديد تذكرة Google Drive المنتهية مع الحفاظ على الملف الجزئي .part للاستئناف من نفس البايت!
                            updateStatusMessage(id, StoreDownloadStatus.RESOLVING, "جاري تجديد جلسة التحميل للاستئناف... 🔄")
                            directUrl = LinkExtractor.extract(initialTask.originalUrl)
                            attempt++
                            delay(1000L)
                        }
                        is StreamOutcome.RestartFromZero -> {
                            partFile.delete()
                            metaFile.delete()
                            attempt++
                        }
                        is StreamOutcome.FatalError -> {
                            partFile.delete()
                            metaFile.delete()
                            markFailed(id, streamOutcome.message)
                            return@withLock
                        }
                        is StreamOutcome.TransientError -> {
                            // إذا انقطعت الشبكة فعلياً، نضع المهمة في PAUSED_NETWORK (SealPlus) بانتظار عودتها تلقائياً
                            if (!isValidatedNetworkAvailable()) {
                                val currentPartLen = partFile.takeIf { it.exists() }?.length() ?: 0L
                                val curTask = _downloadsState.value[id]
                                val total = (curTask?.totalBytes ?: initialTask.expectedSize).coerceAtLeast(currentPartLen)
                                val pct = if (total > 0L) ((currentPartLen * 100L) / total).toInt().coerceIn(0, 99) else (curTask?.progress ?: 0)
                                _downloadsState.update { map ->
                                    val cur = map[id] ?: return@update map
                                    map + (id to cur.copy(
                                        downloadedBytes = currentPartLen,
                                        progress = pct,
                                        speedBytesPerSec = 0L,
                                        status = StoreDownloadStatus.PAUSED_NETWORK,
                                        statusMessage = "انقطع الإنترنت... سيُستأنف تلقائياً من $pct% فور عودة الشبكة 📶"
                                    ))
                                }
                                notifyBroadcast(finalFile.absolutePath, isCompleted = false)
                                return@withLock
                            }

                            if (attempt >= MAX_TRANSIENT_RETRIES) {
                                // نبقي الملف الجزئي .part محفوظاً على القرص لكي يستأنف المستخدم عند الضغط على "استئناف"!
                                markFailed(id, "تقطع الاتصال بسبب ضعف الشبكة، اضغط استئناف للإكمال من حيث توقفت.")
                                return@withLock
                            }

                            attempt++
                            val maxBackoffSec = min(1 shl attempt, 16)
                            val waitMs = Random.nextLong(1500L, (maxBackoffSec * 1000L).coerceAtLeast(2000L))
                            val secText = String.format(Locale.ENGLISH, "%.0f", waitMs / 1000.0)
                            updateStatusMessage(
                                id,
                                StoreDownloadStatus.DOWNLOADING,
                                "تقطع الاتصال... استئناف تلقائي ($attempt/$MAX_TRANSIENT_RETRIES) خلال ${secText}ث 🔄"
                            )
                            delay(waitMs)

                            // إعادة استخراج رابط Google Drive عند كل محاولة لاحقة لتجنب تذكرة uuid منتهية
                            if (LinkExtractor.isGoogleDriveUrl(initialTask.originalUrl)) {
                                directUrl = LinkExtractor.extract(initialTask.originalUrl)
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // إلغاء متعمد من المستخدم
            } catch (e: Throwable) {
                if (id !in cancelledIds) {
                    markFailed(id, "تعذر إكمال التنزيل: ${e.localizedMessage ?: "خطأ غير متوقع"}")
                }
            } finally {
                cancelCallsFor(fileName)
                splitFile.delete()
                jobs.remove(id)
                cleanupIdleResources()
            }
        }
    }

    private sealed class StreamOutcome {
        data object Completed : StreamOutcome()
        data object RefreshUrlAndResume : StreamOutcome()
        data object RestartFromZero : StreamOutcome()
        data class TransientError(val reason: String) : StreamOutcome()
        data class FatalError(val message: String) : StreamOutcome()
    }

    /**
     * 🚀 تسريع التحميل المتوازي (من Orion Store):
     * يفحص دعم HTTP 206 عبر HTTP/1.1 ويقسّم الملف إلى 3 اتصالات متوازية مع مراقب تجمّد (15 ثانية)
     */
    private suspend fun tryParallelSplitDownload(
        task: StoreDownloadTask,
        directUrl: String,
        splitFile: File,
        finalFile: File
    ): Boolean {
        val probeClient = LinkExtractor.splitHttp1Client.newBuilder()
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        val probeReq = buildBaseRequest(directUrl)
            .header("Range", "bytes=0-0")
            .build()

        val probeCall = probeClient.newCall(probeReq)
        activeCalls[task.fileName] = probeCall

        val (resolvedUrl, totalSize) = try {
            probeCall.execute().use { res ->
                val ct = res.header("Content-Type")?.lowercase() ?: ""
                if (res.code != 206 || ct.contains("text/html")) return false
                val total = ContentRangeInfo.parse(res.header("Content-Range"))?.total ?: return false
                res.request.url.toString() to total
            }
        } catch (_: Exception) {
            return false
        } finally {
            activeCalls.remove(task.fileName)
        }

        if (totalSize < MIN_SPLIT_SIZE_BYTES || task.id in cancelledIds) return false

        return try {
            splitFile.delete()
            RandomAccessFile(splitFile, "rw").use { it.setLength(totalSize) }

            val totalDone = AtomicLong(0L)
            val stalled = AtomicBoolean(false)
            val partSize = totalSize / SPLIT_PARTS

            val allSucceeded = coroutineScope {
                val watchdog = launch {
                    var lastSeen = -1L
                    var stillTicks = 0
                    var prevBytes = 0L
                    var prevTime = SystemClock.elapsedRealtime()

                    while (isActive) {
                        delay(500L)
                        val nowBytes = totalDone.get()
                        val nowTime = SystemClock.elapsedRealtime()
                        val elapsedMs = (nowTime - prevTime).coerceAtLeast(1L)
                        val speed = ((nowBytes - prevBytes) * 1000L) / elapsedMs
                        prevBytes = nowBytes
                        prevTime = nowTime

                        val pct = ((nowBytes * 100L) / totalSize).toInt().coerceIn(0, 99)
                        emitProgress(task.id, nowBytes, totalSize, speed, pct)

                        if (nowBytes == lastSeen) {
                            stillTicks++
                            if (stillTicks >= STALL_TIMEOUT_TICKS) {
                                stalled.set(true)
                                cancelCallsFor(task.fileName)
                                break
                            }
                        } else {
                            lastSeen = nowBytes
                            stillTicks = 0
                        }
                    }
                }

                val partResults = (0 until SPLIT_PARTS).map { idx ->
                    async(Dispatchers.IO) {
                        val end = if (idx == SPLIT_PARTS - 1) totalSize - 1L else (idx + 1) * partSize - 1L
                        var pos = idx * partSize

                        repeat(2) { retryIdx ->
                            if (task.id in cancelledIds || stalled.get()) return@async false
                            runCatching {
                                val req = buildBaseRequest(resolvedUrl)
                                    .header("Range", "bytes=$pos-$end")
                                    .build()
                                val call = LinkExtractor.splitHttp1Client.newCall(req)
                                activeCalls["${task.fileName}#$idx"] = call

                                call.execute().use { res ->
                                    val ct = res.header("Content-Type")?.lowercase() ?: ""
                                    if (res.code != 206 || ct.contains("text/html")) return@async false
                                    val body = res.body ?: return@async false

                                    RandomAccessFile(splitFile, "rw").use { out ->
                                        out.seek(pos)
                                        val buf = ByteArray(STREAM_BUFFER_SIZE)
                                        body.byteStream().use { input ->
                                            while (pos <= end) {
                                                if (task.id in cancelledIds || stalled.get()) return@async false
                                                val toRead = min(buf.size.toLong(), end - pos + 1L).toInt()
                                                val n = input.read(buf, 0, toRead)
                                                if (n < 0) break
                                                out.write(buf, 0, n)
                                                pos += n
                                                totalDone.addAndGet(n.toLong())
                                            }
                                        }
                                        out.fd.sync()
                                    }
                                }
                            }
                            if (pos > end) return@async true
                            if (task.id in cancelledIds || stalled.get()) return@async false
                            delay((retryIdx + 1) * 800L)
                        }
                        false
                    }
                }.awaitAll().all { it }

                watchdog.cancel()
                partResults
            }

            if (!allSucceeded || task.id in cancelledIds || stalled.get()) {
                splitFile.delete()
                return false
            }

            if (isHtmlErrorPage(splitFile)) {
                splitFile.delete()
                return false
            }

            moveAtomically(splitFile, finalFile)
        } catch (_: Exception) {
            splitFile.delete()
            false
        }
    }

    /**
     * 🛡️ التحميل الأحادي المحكم (gdown + Komi Store):
     * Range + If-Range + .part/.part.meta + ProgressThrottle + فحص HTML
     */
    private fun runResumableSingleStream(
        task: StoreDownloadTask,
        directUrl: String,
        partFile: File,
        metaFile: File,
        finalFile: File,
        storedMeta: PartialMeta?
    ): StreamOutcome {
        val resumeFrom = if (partFile.exists()) partFile.length() else 0L
        val reqBuilder = buildBaseRequest(directUrl)
        if (resumeFrom > 0L) {
            reqBuilder.header("Range", "bytes=$resumeFrom-")
            val validator = storedMeta?.validator
            if (!validator.isNullOrBlank()) {
                reqBuilder.header("If-Range", validator)
            }
        }

        val call = LinkExtractor.sharedDownloadClient.newCall(reqBuilder.build())
        activeCalls[task.fileName] = call

        call.execute().use { response ->
            val code = response.code
            val contentType = response.header("Content-Type")?.lowercase() ?: ""

            // إذا أعاد Google Drive صفحة HTML بدلاً من الملف (انتهت تذكرة uuid أو تجاوز الحصة)
            if (contentType.startsWith("text/html")) {
                if (LinkExtractor.isGoogleDriveUrl(task.originalUrl)) {
                    val hint = LinkExtractor.lastErrorMessage
                    return if (!hint.isNullOrBlank()) {
                        StreamOutcome.FatalError("تعذر التحميل من Google Drive: $hint")
                    } else if (resumeFrom > 0L) {
                        StreamOutcome.RefreshUrlAndResume
                    } else {
                        StreamOutcome.FatalError("تجاوز رابط Google Drive حد التحميل المؤقت أو يتطلب صلاحية.")
                    }
                }
                return StreamOutcome.FatalError("الرابط أعاد صفحة ويب غير صالحة بدلاً من ملف التثبيت.")
            }

            if (code == 404 || code == 410) {
                return StreamOutcome.FatalError("الملف غير متوفر حالياً على الخادم ($code).")
            }
            if (code == 403 && LinkExtractor.isGoogleDriveUrl(task.originalUrl)) {
                return StreamOutcome.RefreshUrlAndResume
            }

            val contentRange = ContentRangeInfo.parse(response.header("Content-Range"))
            val decision = decideRange(code, resumeFrom, contentRange)

            when (decision) {
                RangeDecision.RESTART -> return StreamOutcome.RestartFromZero
                RangeDecision.UNEXPECTED -> return StreamOutcome.TransientError("استجابة غير متوقعة من الخادم ($code)")
                else -> Unit
            }

            val body = response.body ?: return StreamOutcome.TransientError("استجابة فارغة من الخادم")
            val responseContentLength = body.contentLength()
            val append = decision == RangeDecision.APPEND && resumeFrom > 0L
            val baseOffset = if (append) resumeFrom else 0L

            val totalBytes = when {
                contentRange?.total != null && contentRange.total > 0L -> contentRange.total
                responseContentLength > 0L -> baseOffset + responseContentLength
                task.expectedSize > 0L -> task.expectedSize
                else -> -1L
            }

            // التحقق من تطابق الحجم الكلي عند الاستئناف (Komi Store)
            if (append && storedMeta != null && storedMeta.expectedSize > 0L && totalBytes > 0L && storedMeta.expectedSize != totalBytes) {
                return StreamOutcome.RestartFromZero
            }

            val rawEtag = response.header("ETag")?.takeIf { !it.startsWith("W/") }
            val validator = rawEtag ?: response.header("Last-Modified")
            runCatching {
                metaFile.writeText(
                    PartialMeta(
                        packageName = task.packageName,
                        expectedSize = if (totalBytes > 0L) totalBytes else task.expectedSize,
                        validator = validator
                    ).serialize(),
                    Charsets.UTF_8
                )
            }

            RandomAccessFile(partFile, "rw").use { out ->
                if (append) {
                    out.seek(baseOffset)
                } else {
                    out.setLength(0L)
                }

                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var downloaded = baseOffset
                var lastEmitTime = SystemClock.elapsedRealtime()
                var lastSpeedTime = lastEmitTime
                var lastSpeedBytes = downloaded
                var lastPercent = -1

                body.byteStream().use { input ->
                    while (true) {
                        if (task.id in cancelledIds || !scope.isActive) {
                            out.fd.sync()
                            return StreamOutcome.TransientError("cancelled")
                        }

                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read

                        val now = SystemClock.elapsedRealtime()
                        val pct = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99) else 0
                        val timeDiff = now - lastEmitTime

                        if (pct != lastPercent || timeDiff >= UI_THROTTLE_MS) {
                            val speedElapsed = (now - lastSpeedTime).coerceAtLeast(1L)
                            val speed = if (speedElapsed >= 450L) {
                                val bps = ((downloaded - lastSpeedBytes) * 1000L) / speedElapsed
                                lastSpeedBytes = downloaded
                                lastSpeedTime = now
                                bps
                            } else {
                                _downloadsState.value[task.id]?.speedBytesPerSec ?: 0L
                            }

                            lastEmitTime = now
                            lastPercent = pct
                            emitProgress(task.id, downloaded, totalBytes, speed, pct)
                        }
                    }
                }
                out.fd.sync()
            }

            if (totalBytes > 0L && partFile.length() < totalBytes) {
                return StreamOutcome.TransientError("انقطع التدفق قبل اكتمال الملف")
            }

            if (isHtmlErrorPage(partFile)) {
                partFile.delete()
                metaFile.delete()
                val hint = LinkExtractor.lastErrorMessage
                return StreamOutcome.FatalError(
                    if (!hint.isNullOrBlank()) "تعذر التحميل من Google Drive: $hint"
                    else "تجاوز رابط Google Drive حد التحميل المؤقت."
                )
            }

            return if (moveAtomically(partFile, finalFile)) {
                metaFile.delete()
                StreamOutcome.Completed
            } else {
                StreamOutcome.TransientError("تعذر حفظ الملف النهائي")
            }
        }
    }

    private fun buildBaseRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("User-Agent", LinkExtractor.BROWSER_USER_AGENT)
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")
    }

    private fun moveAtomically(source: File, destination: File): Boolean {
        if (destination.exists()) destination.delete()
        if (source.renameTo(destination)) return true
        return try {
            source.copyTo(destination, overwrite = true)
            source.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

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

    private fun emitProgress(id: Int, downloaded: Long, total: Long, speedBps: Long, percent: Int) {
        val label = formatProgressLabel(downloaded, total, speedBps)
        _downloadsState.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(
                downloadedBytes = downloaded,
                totalBytes = if (total > 0L) total else cur.totalBytes,
                speedBytesPerSec = speedBps,
                progress = percent,
                status = StoreDownloadStatus.DOWNLOADING,
                statusMessage = label
            ))
        }
    }

    private fun updateStatusMessage(id: Int, status: StoreDownloadStatus, msg: String) {
        _downloadsState.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(status = status, speedBytesPerSec = 0L, statusMessage = msg))
        }
    }

    private fun markCompleted(id: Int, finalFile: File) {
        val len = finalFile.length()
        _downloadsState.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(
                downloadedBytes = len,
                totalBytes = len,
                speedBytesPerSec = 0L,
                progress = 100,
                status = StoreDownloadStatus.COMPLETED,
                statusMessage = "اكتمل التحميل بنجاح ✨"
            ))
        }
        notifyBroadcast(finalFile.absolutePath, isCompleted = true)
    }

    private fun markFailed(id: Int, message: String) {
        val filePath = _downloadsState.value[id]?.let { File(downloadsDir(), it.fileName).absolutePath } ?: ""
        _downloadsState.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(
                speedBytesPerSec = 0L,
                status = StoreDownloadStatus.FAILED,
                statusMessage = message
            ))
        }
        notifyBroadcast(filePath, isCompleted = false)
    }

    private fun updateTask(task: StoreDownloadTask) {
        _downloadsState.update { it + (task.id to task) }
    }

    private fun cancelCallsFor(fileName: String) {
        activeCalls.keys
            .filter { it == fileName || it.startsWith("$fileName#") }
            .forEach { key ->
                runCatching { activeCalls.remove(key)?.cancel() }
            }
    }

    fun formatProgressLabel(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String {
        val dlMb = downloadedBytes / (1024.0 * 1024.0)
        val totMb = totalBytes / (1024.0 * 1024.0)
        val speedKB = speedBytesPerSec / 1024.0
        val speedText = if (speedKB >= 1024.0) {
            String.format(Locale.ENGLISH, "%.1f MB/s", speedKB / 1024.0)
        } else {
            String.format(Locale.ENGLISH, "%.1f KB/s", speedKB.coerceAtLeast(0.0))
        }
        return if (totalBytes > 0L) {
            String.format(Locale.ENGLISH, "%s • %.1f/%.1f MB", speedText, dlMb, totMb)
        } else {
            String.format(Locale.ENGLISH, "%s • %.1f MB", speedText, dlMb)
        }
    }

    private fun isValidatedNetworkAvailable(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        } catch (_: Exception) {
            true
        }
    }

    /**
     * 📶 مراقب الشبكة الذكي (تهيئة كسولة عند وجود تحميل نشط فقط لتفادي النشاط الخلفي غير الضروري):
     * يستأنف تلقائياً أي تحميل توقف بسبب انقطاع الشبكة (PAUSED_NETWORK) فور عودة الإنترنت الفعلي
     */
    private fun registerNetworkAutoResumeMonitor() {
        synchronized(networkCallbackLock) {
            if (activeNetworkCallback != null) return
            try {
                val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        resumeNetworkPausedTasks()
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (validated) {
                            resumeNetworkPausedTasks()
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(callback)
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(request, callback)
                }
                activeNetworkCallback = callback
            } catch (_: Exception) {
                activeNetworkCallback = null
            }
        }
    }

    private fun unregisterNetworkAutoResumeMonitor() {
        synchronized(networkCallbackLock) {
            val cb = activeNetworkCallback ?: return
            activeNetworkCallback = null
            try {
                val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
    }

    private fun resumeNetworkPausedTasks() {
        val pausedTasks = _downloadsState.value.values.filter { it.status == StoreDownloadStatus.PAUSED_NETWORK }
        for (task in pausedTasks) {
            if (task.id !in cancelledIds && jobs[task.id]?.isActive != true) {
                enqueue(
                    originalUrl = task.originalUrl,
                    fileName = task.fileName,
                    packageName = task.packageName,
                    displayName = task.displayName,
                    expectedSize = task.expectedSize
                )
            }
        }
    }

    private fun notifyBroadcast(filePath: String, isCompleted: Boolean) {
        try {
            val intent = Intent("com.fix.engine.abdullah.DOWNLOAD_STATE_CHANGED").apply {
                setPackage(appContext.packageName)
                putExtra("FILE_PATH", filePath)
                putExtra("IS_COMPLETED", isCompleted)
            }
            appContext.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AbdullahStore::DownloadWakeLock")
                    .apply { setReferenceCounted(false) }
            }
            // مهلة أمان 30 دقيقة تُجدد عند كل مهمة نشطة وتُحرر فور انتهاء التنزيل
            wakeLock?.acquire(30L * 60L * 1000L)
        } catch (_: Exception) {}
    }

    private fun cleanupIdleResources() {
        val stillRunning = _downloadsState.value.values.any {
            it.status == StoreDownloadStatus.QUEUED ||
                it.status == StoreDownloadStatus.RESOLVING ||
                it.status == StoreDownloadStatus.DOWNLOADING
        }
        if (!stillRunning) {
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (_: Exception) {}
        }
        if (!hasActiveDownloads()) {
            unregisterNetworkAutoResumeMonitor()
        }
    }
}
