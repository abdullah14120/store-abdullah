package com.fix.engine.abdullah.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah - Enterprise Data Model (Secure Edition)
 * Feature: ProGuard Protected Architecture, Smart Size Formatting & Unique File Identity
 */
@Keep // 🚨 حماية فولاذية: يمنع ProGuard و R8 من تغيير أسماء الخصائص لضمان نجاح قراءة الـ JSON دائماً
data class AppModel(
    @SerializedName("id") 
    val id: Int, // 🛠️ تم التصحيح إلى Int ليتطابق مع أرقام السيرفر القياسية الخام

    @SerializedName("name") 
    val name: String,
    
    @SerializedName("packageName") 
    val packageName: String,
    
    @SerializedName("versionName") 
    val versionName: String,
    
    @SerializedName("versionCode") 
    val versionCode: Double, // 🛠️ تم التصحيح إلى Double لدعم الأرقام الضخمة في سيرفرك (مثل التطبيق 9) ومنع الانهيار صامتاً
    
    @SerializedName("developer") 
    val developer: String,
    
    @SerializedName("icon") 
    val iconUrl: String,
    
    @SerializedName("downloadUrl") 
    val downloadUrl: String,

    @SerializedName("size") 
    val size: String = "0", // 🛠️ تم التصحيح إلى String لأن السيرفر يرسل الحجم محاطاً بعلامات تنصيص
    
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
     * 🛠️ تم تحديثها لتقوم بتحويل الـ String القادم من السيرفر إلى قيمة رقمية آمنة في الخلفية
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
