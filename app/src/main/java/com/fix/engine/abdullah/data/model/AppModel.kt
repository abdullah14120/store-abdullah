package com.fix.engine.abdullah.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Enterprise Data Model (Secure & High Performance Edition)
 * Features: ProGuard Protection, Parcelize Speed, Dynamic Size Formatting & Unique Identity
 */
@Keep 
@Parcelize
data class AppModel(
    @SerializedName("id") 
    val id: Int,

    @SerializedName("name") 
    val name: String,
    
    @SerializedName("packageName") 
    val packageName: String,
    
    @SerializedName("versionName") 
    val versionName: String,
    
    @SerializedName("versionCode") 
    val versionCode: Long,
    
    @SerializedName("developer") 
    val developer: String,
    
    @SerializedName("icon") 
    val iconUrl: String,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String,

    @SerializedName("manifestTag") 
    val manifestTag: String? = null,

    @SerializedName("size") 
    val size: String = "0",
    
    @SerializedName("description") 
    val description: String? = "لا يوجد وصف متاح لهذا التطبيق حالياً."
) : Parcelable {
    
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
        val sizeInBytes = size.toLongOrNull() ?: 0L
        
        if (sizeInBytes <= 0L) return "حجم غير معروف"
        
        val kb = sizeInBytes / 1024.0
        val mb = kb / 1024.0
        
        return if (mb >= 1) {
            String.format(Locale.ENGLISH, "%.1f MB", mb)
        } else {
            String.format(Locale.ENGLISH, "%.1f KB", kb)
        }
    }
}
