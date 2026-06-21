package com.fix.engine.abdullah.data.api

import com.fix.engine.abdullah.data.model.AppModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Dynamic Multi-Repo API Engine
 * Feature: Dynamic JSON Fetching with Safe Response Handling
 */
interface ApiService {

    /**
     * جلب قائمة التطبيقات من أي مستودع خارجي.
     * التغليف بـ Response يضمن استقرار التطبيق والتقاط أخطاء الـ HTTP (مثل 404 أو 500) بأمان.
     */
    @GET
    suspend fun getAppsList(@Url url: String): Response<List<AppModel>>
    
}
