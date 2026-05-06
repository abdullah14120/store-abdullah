package com.fix.engine.abdullah.data.api

import com.fix.engine.abdullah.data.model.AppModel
import retrofit2.http.GET
import retrofit2.http.Url

interface ApiService {

    // جلب قائمة التطبيقات من رابط مباشر (المستودع)
    @GET
    suspend fun getAppsList(@Url url: String): List<AppModel>
}
