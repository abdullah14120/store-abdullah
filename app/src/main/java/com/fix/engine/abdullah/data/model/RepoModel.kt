package com.fix.engine.abdullah.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Enterprise Edition
 * Feature: Multi-Repo Support & Time Formatting
 */
data class RepoModel(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("label")
    val label: String, // اسم المستودع (مثلاً: تطبيقات الألعاب)
    
    @SerializedName("url")
    val url: String, // رابط ملف الـ JSON الخام
    
    @SerializedName("lastUpdated")
    val lastUpdated: Long = System.currentTimeMillis()
) : Serializable {

    /**
     * تحويل وقت التحديث إلى تنسيق تاريخ مفهوم للمستخدم.
     */
    fun getFormattedLastUpdate(): String {
        return try {
            val date = Date(lastUpdated)
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "تاريخ غير معروف"
        }
    }
}
