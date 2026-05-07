package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Data Repository
 * Purpose: Acts as a bridge between Remote API and ViewModel
 */
class AppRepository {

    // الوصول للمحرك عبر instance الذي أصلحناه في RetrofitClient
    private val apiService = RetrofitClient.instance

    /**
     * جلب قائمة التطبيقات من مستودع GitHub
     * نستخدم Result لضمان سهولة التعامل مع النجاح والفشل في الـ ViewModel
     */
    suspend fun fetchApps(repoUrl: String): Result<List<AppModel>> {
        return withContext(Dispatchers.IO) { // الانتقال لخييط الـ IO لعدم تعليق الواجهة
            try {
                // استدعاء الواجهة البرمجية
                val response = apiService.getAppsList(repoUrl)
                
                if (response.isNotEmpty()) {
                    Result.success(response)
                } else {
                    // في حال كان الملف فارغاً
                    Result.failure(Exception("قائمة التطبيقات فارغة حالياً"))
                }
            } catch (e: Exception) {
                // التقاط أخطاء الشبكة، انقطاع الاتصال، أو أخطاء الـ JSON
                Result.failure(e)
            }
        }
    }
}
