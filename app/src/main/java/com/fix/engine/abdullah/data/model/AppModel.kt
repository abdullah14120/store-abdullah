package com.fix.engine.abdullah.data.model

import com.google.gson.annotations.SerializedName

data class AppModel(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("name") val name: String,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("icon") val iconUrl: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("developer") val developer: String = "Unknown",
    @SerializedName("description") val description: String? = null,
    @SerializedName("size") val size: Long = 0
)
