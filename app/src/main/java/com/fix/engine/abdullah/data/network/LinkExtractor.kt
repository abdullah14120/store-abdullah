package com.fix.engine.abdullah.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayOutputStream
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
 * Project: Abdullah Store - Unified Link & Session Engine (gdown + Orion Store)
 * Feature: Shared Persistent OkHttp Session & CookieJar, Google Drive #download-form/UUID Bypass,
 *          FallbackDns (DoH), Exponential Backoff & HTTP/1.1 Split Client
 */
object LinkExtractor {

    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private const val MAX_RESOLVE_HOPS = 4
    private const val MAX_NETWORK_RETRIES = 3

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Volatile
    var lastErrorMessage: String? = null
        private set

    val memoryCookieJar: CookieJar = object : CookieJar {
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

    /**
     * الجلسة المشتركة لفك الروابط والتنزيل الأحادي (بدون حد زمني إجمالي callTimeout = 0 للشبكات البطيئة)
     */
    val sharedDownloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(FallbackDns)
            .cookieJar(memoryCookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * عميل HTTP/1.1 مخصص للتحميل المتوازي متعدد الاتصالات (من Orion Store)
     * لأن HTTP/2 يدمج كل الأجزاء في اتصال TCP واحد مقيد السرعة
     */
    val splitHttp1Client: OkHttpClient by lazy {
        sharedDownloadClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    private val probeClient: OkHttpClient by lazy {
        sharedDownloadClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    fun getCookieHeader(targetUrl: String): String? {
        return try {
            val httpUrl = targetUrl.toHttpUrlOrNull() ?: return null
            val cookies = memoryCookieJar.loadForRequest(httpUrl)
            if (cookies.isEmpty()) null else cookies.joinToString("; ") { "${it.name}=${it.value}" }
        } catch (_: Exception) {
            null
        }
    }

    fun isGoogleDriveUrl(url: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase() ?: ""
            host == "drive.google.com" ||
                host == "docs.google.com" ||
                host == "drive.usercontent.google.com" ||
                host.endsWith(".googleusercontent.com")
        } catch (_: Exception) {
            val lower = url.lowercase()
            lower.contains("drive.google.com") ||
                lower.contains("docs.google.com") ||
                lower.contains("drive.usercontent.google.com")
        }
    }

    fun extractGoogleDriveFileId(url: String): String? {
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

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        val cleanUrl = url.trim()

        try {
            if (isGoogleDriveUrl(cleanUrl)) {
                val fileId = extractGoogleDriveFileId(cleanUrl)
                if (!fileId.isNullOrBlank()) {
                    return@withContext resolveGoogleDriveDirectUrl(fileId)
                }
            }

            if (cleanUrl.lowercase().contains("mediafire.com")) {
                return@withContext extractMediaFireLink(cleanUrl)
            }

            resolveStandardRedirectUrl(cleanUrl)
        } catch (_: Exception) {
            cleanUrl
        }
    }

    private suspend fun resolveGoogleDriveDirectUrl(fileId: String): String {
        val fallbackDirectUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        var currentUrl = "https://drive.google.com/uc?id=$fileId&export=download"
        val originUrl = currentUrl

        for (hop in 0 until MAX_RESOLVE_HOPS) {
            val response = executeGetWithBackoff(currentUrl) ?: return fallbackDirectUrl

            response.use { res ->
                val contentDisposition = res.header("Content-Disposition")
                val contentType = res.header("Content-Type")?.lowercase() ?: ""

                if (!contentDisposition.isNullOrBlank() && !contentType.startsWith("text/html")) {
                    return res.request.url.toString()
                }

                val html = readLimitedBody(res, maxBytes = 256 * 1024)

                val gdriveError = extractGdriveErrorMessage(html)
                if (!gdriveError.isNullOrBlank()) {
                    lastErrorMessage = gdriveError
                    return fallbackDirectUrl
                }

                if (currentUrl == originUrl && !res.isSuccessful) {
                    currentUrl = "https://drive.google.com/open?id=$fileId"
                    return@use
                }

                val confirmedUrl = parseGdriveConfirmationHtml(html)
                if (!confirmedUrl.isNullOrBlank()) {
                    if (confirmedUrl == currentUrl) {
                        return confirmedUrl
                    }
                    currentUrl = confirmedUrl
                    return@use
                }

                return fallbackDirectUrl
            }
        }

        return currentUrl.ifBlank { fallbackDirectUrl }
    }

    private fun parseGdriveConfirmationHtml(html: String): String? {
        if (html.isBlank()) return null

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

        val hrefPattern = Pattern.compile("href=[\"'](/uc\\?export=download[^\"']+)[\"']")
        val hrefMatcher = hrefPattern.matcher(html)
        if (hrefMatcher.find()) {
            val path = hrefMatcher.group(1)?.replace("&amp;", "&")
            if (!path.isNullOrBlank()) {
                return "https://docs.google.com$path"
            }
        }

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
        val errPattern = Pattern.compile(
            "<p\\s+class=[\"']uc-error-subcaption[\"'][^>]*>(.*?)</p>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val m = errPattern.matcher(html)
        if (m.find()) {
            return m.group(1)?.replace(Regex("<[^>]+>"), "")?.trim()
        }
        return null
    }

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
                return probeClient.newCall(request).execute()
            } catch (_: IOException) {
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

                probeClient.newCall(request).execute().use { response ->
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
            val out = ByteArrayOutputStream()
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
        return if (lower.endsWith("google.com") || lower.endsWith("googleusercontent.com")) {
            "google.com"
        } else {
            lower
        }
    }

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
