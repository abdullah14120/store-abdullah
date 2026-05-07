package com.fix.engine.abdullah.data.api

import com.fix.engine.abdullah.data.model.AppModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - API Interface
 * Purpose: Dynamically fetch application list from any GitHub Raw URL.
 */
interface ApiService {

    /**
     * جلب قائمة التطبيقات. 
     * نستخدم @Url لتمكين تمرير الرابط الكامل لملف الـ JSON الخام (Raw).
     */
    @GET
    suspend fun getAppsList(@Url url: String): List<AppModel>

    /* ملاحظة للمستقبل: إذا أردت الحصول على معلومات إضافية مثل كود الحالة (200, 404)
       يمكنك استخدام Response<List<AppModel>> بدلاً من القائمة المباشرة.
    */
}
