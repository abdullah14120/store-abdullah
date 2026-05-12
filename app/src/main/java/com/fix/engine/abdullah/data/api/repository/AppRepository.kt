package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Professional Data Repository
 * Logic: Bridge between Remote API and ViewModel with Error Handling
 */
class AppRepository {

    private val apiService = RetrofitClient.instance

    /**
     * جلب قائمة التطبيقات من مستودع GitHub
     * يدعم فحص حالة الاستجابة لضمان استقرار "متجر Abdullah"
     */
    suspend fun fetchApps(repoUrl: String): Result<List<AppModel>> {
        return withContext(Dispatchers.IO) {
            try {
                // استدعاء الواجهة البرمجية (التي تعيد الآن Response)
                val response = apiService.getAppsList(repoUrl)
                
                if (response.isSuccessful) {
                    val apps = response.body()
                    if (!apps.isNullOrEmpty()) {
                        Result.success(apps)
                    } else {
                        Result.failure(Exception("قائمة التطبيقات فارغة في الخادم"))
                    }
                } else {
                    // التقاط أخطاء السيرفر (مثل 404 أو 500)
                    Result.failure(Exception("خطأ في الاتصال بالمستودع: ${response.code()}"))
                }
            } catch (e: Exception) {
                // التقاط أخطاء الشبكة العامة أو تحويل الـ JSON
                Result.failure(e)
            }
        }
    }
}
