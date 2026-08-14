package com.fix.engine.abdullah

import android.content.Context
import android.util.Log

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Native Core Manager with Safe JNI Binding & Fallback Security Handling
 */
object NativeCoreManager {

    private const val TAG = "NativeCoreManager"
    private const val LIB_NAME = "native-lib"

    @Volatile
    private var isLibraryLoaded: Boolean = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        try {
            System.loadLibrary(LIB_NAME)
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "FATAL: Failed to load native library: $LIB_NAME", e)
            isLibraryLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during native library initialization", e)
            isLibraryLoaded = false
        }
    }

    // ==============================================================================
    // 🔒 JNI NATIVE DECLARATIONS
    // ==============================================================================

    @JvmStatic
    private external fun verifySignatureNative(context: Context)

    @JvmStatic
    private external fun getSecureFirebaseUrlNative(): String

    // ==============================================================================
    // 🛡️ SAFE CORE API WRAPPERS
    // ==============================================================================

    /**
     * Executes native signature verification safely without crashing the UI thread.
     * @return [Boolean] true if verification passed natively, false if binding failed or check invalidated.
     */
    fun verifySignatureSafely(context: Context): Boolean {
        if (!isLibraryLoaded) {
            Log.w(TAG, "Execution bypassed: Native library is not loaded.")
            return false
        }

        return try {
            verifySignatureNative(context)
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI Error: Symbol Java_com_fix_engine_abdullah_NativeCoreManager_verifySignatureNative not found.", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Runtime exception during native signature evaluation", e)
            false
        }
    }

    /**
     * Retrieves obfuscated URL string from Native C++ layer.
     * @return [String] Decrypted database URL or empty string fallback.
     */
    fun getSecureFirebaseUrlSafely(): String {
        if (!isLibraryLoaded) {
            Log.w(TAG, "Execution bypassed: Native library is not loaded.")
            return ""
        }

        return try {
            getSecureFirebaseUrlNative()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI Error: Symbol Java_com_fix_engine_abdullah_NativeCoreManager_getSecureFirebaseUrlNative not found.", e)
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Runtime exception during native string extraction", e)
            ""
        }
    }
}
