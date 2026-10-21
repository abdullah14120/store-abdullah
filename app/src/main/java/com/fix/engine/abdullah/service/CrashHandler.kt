package com.fix.engine.abdullah.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Automatic Remote Crash Reporter (Firebase Realtime DB + Local Fallback)
 */
object CrashHandler {

    private const val PREFS_NAME = "AbdullahStoreCrashPrefs"
    private const val KEY_PENDING_CRASH = "pending_crash_json"
    private const val FIREBASE_DB_URL = "https://abdullah-store-a95ed-default-rtdb.europe-west1.firebasedatabase.app"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 1. إرسال أي تقرير انهيار سابق كان محفوظاً محلياً عند انقطاع الإنترنت
        sendPendingCrashIfAny(appContext)

        // 2. التقاط أي انهيار مفاجئ في أي خيط برمجي (Thread) داخل التطبيق
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleCrash(appContext, throwable, "Thread: ${thread.name}", isFatal = true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * تسجيل وإرسال أي خطأ (سواء كان انهياراً كاملاً أو خطأ تم اصطياده بـ try-catch)
     */
    fun recordException(context: Context, throwable: Throwable, locationTag: String) {
        handleCrash(context.applicationContext, throwable, locationTag, isFatal = false)
    }

    private fun handleCrash(context: Context, throwable: Throwable, locationTag: String, isFatal: Boolean) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val fullStackTrace = sw.toString()

        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = timeFormat.format(Date())

        val pInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }

        val versionName = pInfo?.versionName ?: "Unknown"
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})"
        val androidInfo = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ")
        val errorSummary = "${throwable.javaClass.name}: ${throwable.message ?: "No message"}"

        val jsonReport = JSONObject().apply {
            put("timestamp", timestamp)
            put("device", deviceInfo)
            put("androidVersion", androidInfo)
            put("abis", supportedAbis)
            put("appVersion", versionName)
            put("location", locationTag)
            put("isFatal", isFatal)
            put("errorSummary", errorSummary)
            put("stackTrace", fullStackTrace)
        }

        // 1. حفظ التقرير محلياً فوراً لضمان عدم ضياعه أبداً
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_CRASH, jsonReport.toString())
            .commit()

        // 2. إرسال فوري مباشر إلى Firebase عبر HTTP متزامن (ينتظر حتى 2.5 ثانية قبل إغلاق التطبيق)
        val senderThread = Thread {
            val sent = pushToFirebaseRest(jsonReport.toString())
            if (sent) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_PENDING_CRASH)
                    .commit()
            }
        }
        senderThread.start()
        if (isFatal) {
            try {
                senderThread.join(2500)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * إرسال مباشر عبر REST API الخاص بـ Firebase لضمان الوصول حتى لحظة الانهيار
     */
    private fun pushToFirebaseRest(jsonPayload: String): Boolean {
        return try {
            val url = URL("$FIREBASE_DB_URL/crash_logs.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2000
                readTimeout = 2000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e("CrashHandler", "REST push failed: ${e.message}")
            false
        }
    }

    /**
     * إذا فشل الإرسال لحظة الانهيار بسبب ضعف الشبكة، يتم إرساله تلقائياً فور فتح المتجر في المرة التالية
     */
    fun sendPendingCrashIfAny(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingJson = prefs.getString(KEY_PENDING_CRASH, null) ?: return

        Thread {
            if (pushToFirebaseRest(pendingJson)) {
                prefs.edit().remove(KEY_PENDING_CRASH).apply()
            } else {
                // محاولة بديلة عبر Firebase SDK
                try {
                    val obj = JSONObject(pendingJson)
                    val map = mutableMapOf<String, Any>()
                    obj.keys().forEach { key -> map[key] = obj.get(key).toString() }

                    FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                        .getReference("crash_logs")
                        .push()
                        .setValue(map)
                        .addOnSuccessListener {
                            prefs.edit().remove(KEY_PENDING_CRASH).apply()
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }
}
