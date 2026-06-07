# ====================================================================
# Developed by: Abdullah Al-Tamimi
# Project: متجر Abdullah - Official ProGuard/R8 Production Rules
# ====================================================================

# 1. 🚨 حماية موديلات البيانات (Data Models & Repos) من التشويه لضمان قراءة الـ JSON
-keep class com.fix.engine.abdullah.data.model.** { *; }
-dontwarn com.fix.engine.abdullah.data.model.**

# 2. 🔥 درع حماية الفايربيس (Firebase Realtime Database) - ضروري جداً لعمل عداد التنزيلات
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# 3. 🛡️ حماية دوال فحص التوقيع الرقمي (SHA-256 Attestation Security)
-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity {
    private boolean verifyAppSignature();
}
-dontwarn java.security.**

# 4. 🌐 حماية مكتبة Retrofit و OkHttp للاتصالات الآمنة
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# 5. 📸 حماية محرك الصور Glide ومستمعي الحواف الدائرية للماتيريال 3
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-dontwarn com.bumptech.glide.**
-keep class com.bumptech.glide.** { *; }

# 6. 🎨 حماية واجهات الـ ViewBinding والـ ViewModel والأنيميشن الحركي للماتيريال
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * implements androidx.viewbinding.ViewBinding { *; }
-keep class androidx.transition.** { *; }
-keep class com.google.android.material.transition.** { *; }
-keep class com.google.android.material.** { *; }

# 7. 📦 حماية الـ WorkManager والمهام الخلفية المستقرة للتحديثات
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# 8. 🛠️ قواعد عامة لتقليل الأخطاء وحذف ملفات التتبع الحساسة في نسخة الريليز
-dontnote **
-keepattributes SourceFile, LineNumberTable

# 9. 🚀 حماية مكتبة Fetch للتحميلات (التي أضفناها للتو)
-keep class com.tonyodev.fetch2.** { *; }
-keep class com.tonyodev.fetch2core.** { *; }
-dontwarn com.tonyodev.fetch2.**
-dontwarn com.tonyodev.fetch2core.**

# 10. 🗄️ حماية مكتبة Room Database (تستخدمها Fetch داخلياً لحفظ تقدم التحميل واستئنافه)
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# 11. 🧩 حماية مكتبة Gson (لمنع فشل تحويل الـ JSON إلى Models)
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.** { *; }
-dontwarn com.google.gson.**

# 12. ⚡ حماية الـ Coroutines (تستخدمها WorkManager و Room في الخلفية)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
