package com.fix.engine.abdullah

import android.app.Application
import com.google.firebase.FirebaseApp

class FixEngineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // تهيئة الفايربيس فوراً عند إقلاع التطبيق في الخلفية
        FirebaseApp.initializeApp(this)
    }
}
