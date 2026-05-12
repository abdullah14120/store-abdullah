package com.fix.engine.abdullah.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Enterprise Data Model
 * Feature: Smart Size Formatting & Unique File Identity
 */
data class AppModel(
    @SerializedName("packageName") 
    val packageName: String,
    
    @SerializedName("name") 
    val name: String,
    
    @SerializedName("versionName") 
    val versionName: String,
    
    @SerializedName("versionCode") 
    val versionCode: Long, // تم التحويل لـ Long لدعم التوافقية العالية
    
    @SerializedName("icon") 
    val iconUrl: String,
    
    @SerializedName("downloadUrl") 
    val downloadUrl: String,
    
    @SerializedName("developer") 
    val developer: String = "مطور معتمد",
    
    @SerializedName("description") 
    val description: String? = "لا يوجد وصف متاح لهذا التطبيق حالياً.",
    
    @SerializedName("size") 
    val size: Long = 0, // الحجم بالبايت (Bytes)
    
    @SerializedName("category")
    val category: String = "عام"

) : Serializable {
    
    /**
     * توليد اسم الملف الفريد الموحد لكل أجزاء التطبيق.
     * يضمن عدم تداخل الملفات المحملة.
     */
    fun getUniqueFileName(): String {
        return "${packageName}_v${versionCode}.apk"
    }

    /**
     * وظيفة احترافية لتحويل الحجم ديناميكياً (MB أو KB).
     */
    fun getFormattedSize(): String {
        if (size <= 0) return "حجم غير معروف"
        
        val kb = size / 1024.0
        val mb = kb / 1024.0
        
        return if (mb >= 1) {
            String.format(Locale.ENGLISH, "%.1f MB", mb)
        } else {
            String.format(Locale.ENGLISH, "%.1f KB", kb)
        }
    }
}
