package com.fix.engine.abdullah

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import androidx.appcompat.app.AppCompatDelegate
import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.service.CrashHandler
import com.fix.engine.abdullah.service.StoreDownloadEngine
import java.io.File
import java.util.zip.ZipFile

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Android 14/15/16 Safe Core & Unified OkHttp Download Engine
 */
class AbdullahStoreApp : Application() {

    override fun getApplicationContext(): Context = this

    private fun ensureSafeReceiverFlags(flags: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasExportedFlag = (flags and Context.RECEIVER_EXPORTED) != 0 ||
                (flags and Context.RECEIVER_NOT_EXPORTED) != 0
            if (hasExportedFlag) flags else (flags or Context.RECEIVER_NOT_EXPORTED)
        } else {
            flags
        }
    }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.registerReceiver(receiver, filter, ensureSafeReceiverFlags(flags))
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(
                receiver,
                filter,
                broadcastPermission,
                scheduler,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.registerReceiver(
                receiver,
                filter,
                broadcastPermission,
                scheduler,
                ensureSafeReceiverFlags(flags)
            )
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("FixEnginePrefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        appInstance = this
        CrashHandler.init(this)

        try {
            RetrofitClient.init(this)
            StoreDownloadEngine.init(this)
        } catch (t: Throwable) {
            CrashHandler.recordException(this, t, "AbdullahStoreApp.initEngines")
        }
    }

    companion object {
        @Volatile
        var appInstance: AbdullahStoreApp? = null
            private set

        fun isApkCompleteAndValid(context: Context, file: File): Boolean {
            if (!file.exists() || file.length() <= 0L) return false

            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageArchiveInfo(
                        file.absolutePath,
                        PackageManager.PackageInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                }
                if (info != null) return true
            } catch (_: Exception) {}

            return isSplitApksBundle(file)
        }

        fun isSplitApksBundle(file: File): Boolean {
            if (!file.exists() || file.length() <= 0L) return false
            return try {
                ZipFile(file).use { zip ->
                    var hasApkEntries = false
                    var hasRootManifest = false
                    val entries = zip.entries()

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name.lowercase()
                        if (name == "androidmanifest.xml") {
                            hasRootManifest = true
                        } else if (!entry.isDirectory && name.endsWith(".apk") && entry.size > 0L) {
                            hasApkEntries = true
                        }
                    }
                    hasApkEntries && !hasRootManifest
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
