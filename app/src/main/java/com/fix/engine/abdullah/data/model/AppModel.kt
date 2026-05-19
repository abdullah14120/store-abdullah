package com.fix.engine.abdullah.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Enterprise Data Model
 * Feature: ProGuard Protected Architecture, Smart Size Formatting & Unique File Identity
 */
@Keep // 🚨 حماية فولاذية: يمنع ProGuard و R8 من تغيير أسماء الخصائص لضمان نجاح قراءة الـ JSON دائماً
data class AppModel(
    @SerializedName("id") 
    val id: String,

    @SerializedName("name") 
    val name: String,
    
    @SerializedName("packageName") 
    val packageName: String,
    
    @SerializedName("versionName") 
    val versionName: String,
    
    @SerializedName("versionCode") 
    val versionCode: Long, // 🛠️ تم التصحيح: تحويله إلى Long ليتوافق مع أنظمة أندرويد الحديثة ومحاكاة المتاجر الكبرى
    
    @SerializedName("developer") 
    val developer: String,
    
    @SerializedName("icon") 
    val iconUrl: String,
    
    @SerializedName("downloadUrl") 
    val downloadUrl: String,

    @SerializedName("size") 
    val size: Long = 0, // الحجم بالبايت (Bytes) قادم من السيرفر ليتم تحويله تلقائياً
    
    @SerializedName("description") 
    val description: String? = "لا يوجد وصف متاح لهذا التطبيق حالياً."
) : Serializable {
    
    /**
     * توليد اسم الملف الفريد الموحد لكل أجزاء التطبيق.
     * يضمن عدم تداخل الملفات المحملة داخل مجلد التنزيلات بالجهاز.
     */
    fun getUniqueFileName(): String {
        return "${packageName}_v${versionName.trim()}.apk"
    }

    /**
     * وظيفة احترافية لتحويل الحجم ديناميكياً (MB أو KB) بالاعتماد على النظام القياسي
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
