package com.fix.engine.abdullah.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE
 * Description: Data model for application items, enabled for Intent transfer.
 */
data class AppModel(
    @SerializedName("packageName") 
    val packageName: String,
    
    @SerializedName("name") 
    val name: String,
    
    @SerializedName("versionName") 
    val versionName: String,
    
    @SerializedName("versionCode") 
    val versionCode: Int,
    
    @SerializedName("icon") 
    val iconUrl: String,
    
    @SerializedName("downloadUrl") 
    val downloadUrl: String,
    
    @SerializedName("developer") 
    val developer: String = "Unknown",
    
    @SerializedName("description") 
    val description: String? = null,
    
    @SerializedName("size") 
    val size: Long = 0,
    
    @SerializedName("category")
    val category: String = "General"

) : Serializable {
    
    /**
     * وظيفة مساعدة لتحويل الحجم من بايت إلى ميجابايت بشكل أنيق
     */
    fun getFormattedSize(): String {
        return if (size <= 0) "Unknown Size" 
        else String.format(Locale.US, "%.2f MB", size.toDouble() / (1024 * 1024))
    }
}
