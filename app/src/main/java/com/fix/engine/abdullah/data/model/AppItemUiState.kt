package com.fix.engine.abdullah.data.model

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: UI State Wrapper
 * Description: الحاوية التي تغلف بيانات التطبيق الخام مع حالته الجاهزة للعرض المباشر في الـ Adapter.
 */
data class AppItemUiState(
    val app: AppModel,                   // الموديل الأصلي القادم من السيرفر
    val status: AppInstallStatus,        // الحالة الجاهزة (تنزيل، تثبيت، تحديث، فتح)
    val installedVersion: String = ""    // رقم الإصدار المثبت حالياً (لعرضه في حالة التحديث)
)
