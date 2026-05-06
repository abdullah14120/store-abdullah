package com.fix.engine.abdullah.data.model

data class RepoModel(
    val id: String,
    val label: String,
    val url: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
