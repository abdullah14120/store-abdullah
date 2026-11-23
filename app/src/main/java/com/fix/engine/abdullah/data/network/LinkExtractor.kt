package com.fix.engine.abdullah.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.min
import kotlin.random.Random

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Advanced Link Engine (Enhanced with gdown Resolution & Cookie Strategy)
 * Feature: Full Google Drive Confirmation/UUID Form Bypass, Session Cookie Forwarding,
 *          Exponential Backoff Retries for Weak Networks & MediaFire Scraper
 */
object LinkExtractor {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val MAX_RESOLVE_HOPS = 4
    private const val MAX_NETWORK_RETRIES = 3

    // تخزين الكوكيز الخاصة بجلسة Google Drive في الذاكرة لتمريرها مباشرة إلى محرك التحميل Fetch2
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Volatile
    var lastErrorMessage: String? = null
        private set

    private val memoryCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val hostKey = normalizeCookieDomain(url.host)
            val existing = cookieStore.getOrPut(hostKey) { mutableListOf() }
            synchronized(existing) {
                for (newCookie in cookies) {
                    existing.removeAll { it.name == newCookie.name && it.domain == newCookie.domain }
                    if (newCookie.expiresAt > System.currentTimeMillis()) {
                        existing.add(newCookie)
                    }
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val matched = mutableListOf<Cookie>()
            for ((_, list) in cookieStore) {
                synchronized(list) {
                    list.removeAll { it.expiresAt <= now }
                    for (c in list) {
                        if (c.matches(url)) {
                            matched.add(c)
                        }
                    }
                }
            }
            return matched
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(memoryCookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * استرجاع ترويسة Cookie الصالحة لرابط معين لكي يرفقها AndroidDownloadManager مع طلب التحميل
     */
    fun getCookieHeader(targetUrl: String): String? {
        return try {
            val httpUrl = targetUrl.toHttpUrlOrNull() ?: return null
            val cookies = memoryCookieJar.loadForRequest(httpUrl)
            if (cookies.isEmpty()) null else cookies.joinToString("; ") { "${it.name}=${it.value}" }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        val cleanUrl = url.trim()

        try {
            // 1. فحص ومعالجة روابط Google Drive / Google Docs بجميع صيغها (مستوحى من gdown/parse_url.py)
            if (isGoogleDriveUrl(cleanUrl)) {
                val fileId = extractGoogleDriveFileId(cleanUrl)
                if (!fileId.isNullOrBlank()) {
                    return@withContext resolveGoogleDriveDirectUrl(fileId)
                }
            }

            // 2. معالجة روابط MediaFire (كشط رابط التنزيل المباشر)
            if (cleanUrl.lowercase().contains("mediafire.com")) {
                return@withContext extractMediaFireLink(cleanUrl)
            }

            // 3. الروابط العادية أو روابط GitHub / Cloudflare باستخدام طلب HEAD سريع مع إعادة محاولة
            resolveStandardRedirectUrl(cleanUrl)
        } catch (e: Exception) {
            cleanUrl
        }
    }

    /**
     * التحقق مما إذا كان الرابط يتبع نطاقات Google Drive (من parse_url.py)
     */
    private fun isGoogleDriveUrl(url: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase() ?: ""
            host == "drive.google.com" ||
                host == "docs.google.com" ||
                host == "drive.usercontent.google.com"
        } catch (_: Exception) {
            val lower = url.lowercase()
            lower.contains("drive.google.com") ||
                lower.contains("docs.google.com") ||
                lower.contains("drive.usercontent.google.com")
        }
    }

    /**
     * استخراج معرف الملف (File ID) من جميع صيغ روابط Google Drive (من parse_url.py)
     */
    private fun extractGoogleDriveFileId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("/file/d/([-\\w]+)"),
            Pattern.compile("/file/u/[0-9]+/d/([-\\w]+)"),
            Pattern.compile("/(?:document|spreadsheets|presentation)/d/([-\\w]+)"),
            Pattern.compile("/(?:document|spreadsheets|presentation)/u/[0-9]+/d/([-\\w]+)"),
            Pattern.compile("[?&]id=([-\\w]+)")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                val id = matcher.group(1)
                if (!id.isNullOrBlank()) return id
            }
        }
        return null
    }

    /**
     * محرك تخطي صفحة التحذير واستخراج رابط التحميل الحقيقي من Google Drive (مستوحى من gdown/download.py)
     * يقرأ نموذج #download-form ويجمع كافة الحقول المخفية (confirm, uuid, at, id, export) مع حفظ الكوكيز.
     */
    private suspend fun resolveGoogleDriveDirectUrl(fileId: String): String {
        val fallbackDirectUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        var currentUrl = "https://drive.google.com/uc?id=$fileId&export=download"
        val originUrl = currentUrl

        for (hop in 0 until MAX_RESOLVE_HOPS) {
            val response = executeGetWithBackoff(currentUrl) ?: return fallbackDirectUrl

            response.use { res ->
                val contentDisposition = res.header("Content-Disposition")
                val contentType = res.header("Content-Type")?.lowercase() ?: ""

                // إذا وجدنا Content-Disposition ولم تكن الصفحة HTML، فهذا هو الرابط المباشر الحقيقي للملف!
                if (!contentDisposition.isNullOrBlank() && !contentType.startsWith("text/html")) {
                    return res.request.url.toString()
                }

                // قراءة محتوى صفحة HTML بأمان (بحد أقصى 256 كيلوبايت لتفادي تحميل ملف ثنائي كامل بالخطأ)
                val html = readLimitedBody(res, maxBytes = 256 * 1024)

                // التحقق من وجود رسالة خطأ من Google Drive (مثل تجاوز الحصة أو عدم وجود صلاحية)
                val gdriveError = extractGdriveErrorMessage(html)
                if (!gdriveError.isNullOrBlank()) {
                    lastErrorMessage = gdriveError
                    return fallbackDirectUrl
                }

                // إذا فشل الطلب الأولي ولم تظهر رسالة خطأ، نحاول المسار البديل drive.google.com/open (كما في gdown)
                if (currentUrl == originUrl && !res.isSuccessful) {
                    currentUrl = "https://drive.google.com/open?id=$fileId"
                    return@use
                }

                // استخراج رابط التأكيد الحقيقي من HTML عبر خوارزميات gdown الثلاث
                val confirmedUrl = parseGdriveConfirmationHtml(html)
                if (!confirmedUrl.isNullOrBlank()) {
                    if (confirmedUrl == currentUrl) {
                        return confirmedUrl
                    }
                    currentUrl = confirmedUrl
                    return@use
                }

                // إذا لم نعثر على نموذج تأكيد لكن الرابط النهائي هو usercontent، نعود للرابط المباشر القياسي
                return fallbackDirectUrl
            }
        }

        return currentUrl.ifBlank { fallbackDirectUrl }
    }

    /**
     * تحليل صفحة تأكيد Google Drive (مطابق لـ get_url_from_gdrive_confirmation في download.py)
     */
    private fun parseGdriveConfirmationHtml(html: String): String? {
        if (html.isBlank()) return null

        // 1. البحث عن نموذج #download-form واستخراج action وجميع حقول <input type="hidden"> (مثل uuid و confirm و at)
        val formPattern = Pattern.compile(
            "<form[^>]*id=[\"']download-form[\"'][^>]*action=[\"']([^\"']+)[\"'][^>]*>(.*?)</form>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val formMatcher = formPattern.matcher(html)
        if (formMatcher.find()) {
            val rawAction = formMatcher.group(1)?.replace("&amp;", "&") ?: ""
            val formBody = formMatcher.group(2) ?: ""
            if (rawAction.isNotBlank()) {
                return buildUrlWithHiddenInputs(rawAction, formBody)
            }
        }

        // صيغة بديلة في حال جاء action قبل id في وسم form
        val formAltPattern = Pattern.compile(
            "<form[^>]*action=[\"']([^\"']+)[\"'][^>]*id=[\"']download-form[\"'][^>]*>(.*?)</form>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val formAltMatcher = formAltPattern.matcher(html)
        if (formAltMatcher.find()) {
            val rawAction = formAltMatcher.group(1)?.replace("&amp;", "&") ?: ""
            val formBody = formAltMatcher.group(2) ?: ""
            if (rawAction.isNotBlank()) {
                return buildUrlWithHiddenInputs(rawAction, formBody)
            }
        }

        // 2. البحث عن رابط مباشر بصيغة href="/uc?export=download..."
        val hrefPattern = Pattern.compile("href=[\"'](/uc\\?export=download[^\"']+)[\"']")
        val hrefMatcher = hrefPattern.matcher(html)
        if (hrefMatcher.find()) {
            val path = hrefMatcher.group(1)?.replace("&amp;", "&")
            if (!path.isNullOrBlank()) {
                return "https://docs.google.com$path"
            }
        }

        // 3. البحث عن "downloadUrl":"..." داخل JSON مضمّن في الصفحة
        val jsonUrlPattern = Pattern.compile("\"downloadUrl\":\"([^\"]+)\"")
        val jsonUrlMatcher = jsonUrlPattern.matcher(html)
        if (jsonUrlMatcher.find()) {
            val rawUrl = jsonUrlMatcher.group(1)
                ?.replace("\\u003d", "=")
                ?.replace("\\u0026", "&")
                ?.replace("\\/", "/")
            if (!rawUrl.isNullOrBlank()) {
                return rawUrl
            }
        }

        return null
    }

    private fun buildUrlWithHiddenInputs(actionUrl: String, formBody: String): String {
        val params = LinkedHashMap<String, String>()

        // استخراج المعاملات الموجودة مسبقاً في رابط action إن وجدت
        val qIndex = actionUrl.indexOf('?')
        val baseUrl = if (qIndex >= 0) actionUrl.substring(0, qIndex) else actionUrl
        if (qIndex >= 0 && qIndex < actionUrl.length - 1) {
            actionUrl.substring(qIndex + 1).split("&").forEach { pair ->
                val eqIdx = pair.indexOf('=')
                if (eqIdx > 0) {
                    params[pair.substring(0, eqIdx)] = pair.substring(eqIdx + 1)
                }
            }
        }

        // استخراج كل وسوم <input type="hidden" ...>
        val inputTagPattern = Pattern.compile("<input[^>]+type=[\"']hidden[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
        val namePattern = Pattern.compile("name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val valuePattern = Pattern.compile("value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)

        val tagMatcher = inputTagPattern.matcher(formBody)
        while (tagMatcher.find()) {
            val tag = tagMatcher.group()
            val nameM = namePattern.matcher(tag)
            val valM = valuePattern.matcher(tag)
            if (nameM.find() && valM.find()) {
                val pName = nameM.group(1) ?: continue
                val pVal = valM.group(1) ?: ""
                params[pName] = URLEncoder.encode(pVal, "UTF-8")
            }
        }

        if (params.isEmpty()) return actionUrl
        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$baseUrl?$queryString"
    }

    private fun extractGdriveErrorMessage(html: String): String? {
        val errPattern = Pattern.compile("<p\\s+class=[\"']uc-error-subcaption[\"'][^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val m = errPattern.matcher(html)
        if (m.find()) {
            return m.group(1)?.replace(Regex("<[^>]+>"), "")?.trim()
        }
        return null
    }

    /**
     * تنفيذ طلب GET مع إعادة محاولة تلقائية وتدرج زمني (Exponential Backoff + Jitter) للشبكات الضعيفة
     */
    private suspend fun executeGetWithBackoff(url: String): okhttp3.Response? {
        var attempt = 0
        while (attempt <= MAX_NETWORK_RETRIES) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept-Encoding", "identity")
                    .build()
                return client.newCall(request).execute()
            } catch (e: IOException) {
                if (attempt == MAX_NETWORK_RETRIES) return null
                val maxDelaySec = min(1 shl attempt, 10)
                val jitterMs = Random.nextLong(300L, (maxDelaySec * 1000L).coerceAtLeast(500L))
                delay(jitterMs)
                attempt++
            }
        }
        return null
    }

    private suspend fun resolveStandardRedirectUrl(url: String): String {
        var attempt = 0
        while (attempt <= 2) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept-Encoding", "identity")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.isRedirect) {
                        return response.request.url.toString()
                    }
                    return url
                }
            } catch (_: IOException) {
                if (attempt == 2) return url
                delay(600L * (attempt + 1))
                attempt++
            }
        }
        return url
    }

    private fun readLimitedBody(response: okhttp3.Response, maxBytes: Int): String {
        return try {
            val stream = response.body?.byteStream() ?: return ""
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (total < maxBytes) {
                val read = stream.read(buf, 0, min(buf.size, maxBytes - total))
                if (read == -1) break
                out.write(buf, 0, read)
                total += read
            }
            out.toString("UTF-8")
        } catch (_: Exception) {
            ""
        }
    }

    private fun normalizeCookieDomain(host: String): String {
        val lower = host.lowercase()
        return if (lower.endsWith("google.com")) "google.com" else lower
    }

    /**
     * كشط روابط ميديا فاير واستخراج رابط الـ APK المباشر
     */
    private suspend fun extractMediaFireLink(mediaFireUrl: String): String {
        try {
            val response = executeGetWithBackoff(mediaFireUrl) ?: return mediaFireUrl
            response.use { res ->
                if (res.isSuccessful) {
                    val htmlBody = readLimitedBody(res, maxBytes = 512 * 1024)
                    val pattern = Pattern.compile("href=\"(https?://[a-zA-Z0-9-]+\\.mediafire\\.com/download/[^\"]+)\"")
                    val matcher = pattern.matcher(htmlBody)
                    if (matcher.find()) {
                        return matcher.group(1) ?: mediaFireUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mediaFireUrl
    }
}
