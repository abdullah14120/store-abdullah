package com.fix.engine.abdullah.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah - Enterprise Data Model (Secure & High Performance Edition)
 * Feature: ProGuard Protected Architecture, Smart Size Formatting, Unique File Identity & Parcelize Speed
 */
@Keep 
@Parcelize // 🚀 التعديل الجوهري: استخدام تقنية أندرويد الأصلية لتمرير البيانات بسرعة فائقة بين الشاشات
data class AppModel(
    @SerializedName("id") 
    val id: Int, // 🟢 تم توحيد النوع برمجياً ليطابق التعليق والسيرفر

    @SerializedName("name") 
    val name: String,
    
    @SerializedName("packageName") 
    val packageName: String,
    
    @SerializedName("versionName") 
    val versionName: String,
    
    @SerializedName("versionCode") 
    val versionCode: Long, // 🟢 تم التصحيح إلى Long لدعم الأرقام الصحيحة الضخمة بدون أخطاء الفاصلة العشرية
    
    @SerializedName("developer") 
    val developer: String,
    
    @SerializedName("icon") 
    val iconUrl: String,
    
    @SerializedName("downloadUrl") 
    val downloadUrl: String,

    @SerializedName("size") 
    val size: String = "0", // يبقى String ليتعامل بسلاسة مع علامات التنصيص القادمة من JSON
    
    @SerializedName("description") 
    val description: String? = "لا يوجد وصف متاح لهذا التطبيق حالياً."
) : Parcelable { // 🚀 استبدال Serializable بـ Parcelable
    
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
        // تحويل النص الآتي من السيرفر إلى رقم طويل (Long) بأمان
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
