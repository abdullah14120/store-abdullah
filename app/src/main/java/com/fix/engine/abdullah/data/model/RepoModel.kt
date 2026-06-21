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
 * Architecture: Enterprise Multi-Repo Data Model
 * Features: ProGuard Protection, Parcelize Speed & RTL Safe Time Formatting
 */
@Keep 
@Parcelize 
data class RepoModel(
    @SerializedName("id")
    val id: String, 
    
    @SerializedName("label")
    val label: String, 
    
    @SerializedName("url")
    val url: String, 
    
    @SerializedName("lastUpdated")
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable { 

    /**
     * تحويل وقت التحديث إلى تنسيق تاريخ رقمي منسق ومستقر تماماً في الواجهات العربية.
     */
    fun getFormattedLastUpdate(): String {
        return try {
            val date = Date(lastUpdated)
            // استخدام Locale.US يمنع انقلاب التاريخ في الأجهزة التي تعمل باللغة العربية
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            format.format(date)
        } catch (e: Exception) {
            "تاريخ غير معروف"
        }
    }
}
