package com.fix.engine.abdullah.data.model

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Clean State Management Enum
 * Description: يمثل الحالات الأربع الممكنة لأي تطبيق لتبسيط واجهة المستخدم ومنع التقطيع.
 */
enum class AppInstallStatus {
    DOWNLOADED,         // الملف محمل مسبقاً وجاهز للتثبيت فوراً
    UPDATE_AVAILABLE,   // التطبيق مثبت ولكن يوجد إصدار أحدث في السيرفر
    INSTALLED,          // التطبيق مثبت ومُحدث لآخر إصدار
    NOT_INSTALLED       // التطبيق غير موجود نهائياً على الجهاز
}
