package com.fix.engine.abdullah.data.model

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: UI State Wrapper (Hybrid JSON + Firebase Edition)
 */
data class AppItemUiState(
    val app: AppModel,                   
    val status: AppInstallStatus,        
    val installedVersion: String = "",   
    val downloadsCount: Long = 0L        // 📊 العداد التفاعلي الحي من الفايربيس
)
