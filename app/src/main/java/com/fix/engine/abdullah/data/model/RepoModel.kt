package com.fix.engine.abdullah.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Enterprise Edition
 * Feature: ProGuard Protected Multi-Repo Support & RTL Fixed Time Formatting
 */
@Keep // 🚨 درع الحماية: يمنع تدمير أو تشويه أسماء المتغيرات أثناء تشفير الـ APK الخارجي
data class RepoModel(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("label")
    val label: String, // اسم المستودع (مثلاً: تطبيقات عبدالله المعدلة)
    
    @SerializedName("url")
    val url: String, // رابط ملف الـ JSON الخام الخاص بالمستودع الفرعي
    
    @SerializedName("lastUpdated")
    val lastUpdated: Long = System.currentTimeMillis()
) : Serializable {

    /**
     * تحويل وقت التحديث إلى تنسيق تاريخ رقمي منسق ومستقر تماماً في الواجهات العربية.
     */
    fun getFormattedLastUpdate(): String {
        return try {
            val date = Date(lastUpdated)
            // استخدامLocale.US يضمن ظهور الأرقام والشرطات بالترتيب الصحيح (السنة/الشهر/اليوم) دون انقلابه بسبب اتجاه نظام الهاتف
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            format.format(date)
        } catch (e: Exception) {
            "تاريخ غير معروف"
        }
    }
}
