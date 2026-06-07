package com.fix.engine.abdullah

import android.app.Application
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration

class AbdullahStoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 🚀 تهيئة مكتبة Fetch مع السماح بـ 3 مسارات تحميل في نفس الوقت للسرعة القصوى
        val fetchConfiguration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(3) 
            .build()
            
        Fetch.Impl.setDefaultInstanceConfiguration(fetchConfiguration)
    }
}
