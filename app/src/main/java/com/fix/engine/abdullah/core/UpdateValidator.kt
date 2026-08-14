package com.fix.engine.abdullah.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.fix.engine.abdullah.data.model.AppModel

object UpdateValidator {
    fun getMandatoryUpdate(context: Context, apps: List<AppModel>): AppModel? {
        val storeApp = apps.find { it.packageName == context.packageName } ?: return null
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (storeApp.versionName.trim() != pInfo.versionName?.trim()) storeApp else null
        } catch (e: Exception) {
            null
        }
    }
}
