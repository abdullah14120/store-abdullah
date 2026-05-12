package com.fix.engine.abdullah.data.api

import com.fix.engine.abdullah.data.model.AppModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Professional API Engine
 * Feature: Dynamic JSON Fetching with Error Handling Support
 */
interface ApiService {

    /**
     * جلب قائمة التطبيقات من مستودع GitHub.
     * تم استخدام Response<List<AppModel>> لضمان استقرار التطبيق عند حدوث أخطاء في الشبكة.
     */
    @GET
    suspend fun getAppsList(@Url url: String): Response<List<AppModel>>

    /**
     * ملاحظة تقنية: 
     * استخدام @Url يمنح متجر Abdullah مرونة عالية، حيث يمكنك تغيير مصدر 
     * التطبيقات (الـ JSON) برمجياً دون الحاجة لتحديث التطبيق نفسه.
     */
}
