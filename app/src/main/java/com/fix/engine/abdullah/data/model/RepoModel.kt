package com.fix.engine.abdullah.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Enterprise Edition
 * Feature: ProGuard Protected Multi-Repo Support, RTL Fixed Time Formatting & Parcelize Speed
 */
@Keep // 🚨 درع الحماية: يمنع تدمير أو تشويه أسماء المتغيرات أثناء تشفير الـ APK الخارجي
@Parcelize // 🚀 التعديل الجوهري: استخدام تقنية أندرويد الأصلية لتمرير البيانات بسرعة فائقة
data class RepoModel(
    @SerializedName("id")
    val id: String, // 💡 ملاحظة: إذا كان السيرفر يرسل الـ ID كرقم خام (بدون علامات تنصيص)، فقم بتغييرها إلى Int كما فعلنا في AppModel
    
    @SerializedName("label")
    val label: String, // اسم المستودع (مثلاً: تطبيقات عبدالله المعدلة)
    
    @SerializedName("url")
    val url: String, // رابط ملف الـ JSON الخام الخاص بالمستودع الفرعي
    
    @SerializedName("lastUpdated")
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable { // 🚀 التعديل الجوهري: استبدال Serializable البطيئة

    /**
     * تحويل وقت التحديث إلى تنسيق تاريخ رقمي منسق ومستقر تماماً في الواجهات العربية.
     */
    fun getFormattedLastUpdate(): String {
        return try {
            val date = Date(lastUpdated)
            // استخدام Locale.US يضمن ظهور الأرقام والشرطات بالترتيب الصحيح (السنة/الشهر/اليوم) دون انقلابه بسبب اتجاه نظام الهاتف
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            format.format(date)
        } catch (e: Exception) {
            "تاريخ غير معروف"
        }
    }
}
