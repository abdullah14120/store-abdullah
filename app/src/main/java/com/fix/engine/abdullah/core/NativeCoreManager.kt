package com.fix.engine.abdullah.core

import android.content.Context
import android.util.Log

object NativeCoreManager {
    private const val TAG = "NativeCoreManager"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("native-lib")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    @JvmStatic private external fun verifySignatureNative(context: Context)
    @JvmStatic private external fun getSecureRepoUrl(): String

    fun verifySignatureSafely(context: Context): Boolean {
        if (!isLoaded) return false
        return try {
            verifySignatureNative(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Native signature verification failed", e)
            false
        }
    }

    fun getRepoUrlSafely(): String {
        if (!isLoaded) return ""
        return try {
            getSecureRepoUrl()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve secure repo URL", e)
            ""
        }
    }
}
