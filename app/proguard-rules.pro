# ====================================================================
# Developed by: Abdullah Al-Tamimi
# Project: متجر Abdullah - Play-Protect Transparent R8 Rules
# ====================================================================

# إبقاء أسماء الكلاسات والدوال واضحة لفاحص Google Play Protect مع استمرار R8 بتقليص الحجم وحذف الكود غير المستخدم
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keeppackagenames com.fix.engine.abdullah.**

# حماية دوال الـ Enum الأساسية
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# حماية الموديلات والـ Gson والـ Parcelize
-keep class com.fix.engine.abdullah.data.model.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.** { *; }
-dontwarn com.google.gson.**

-keepnames class * extends android.os.Parcelable
-keepclassmembers class * extends android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# حماية المستقبلات والخدمات
-keep class com.fix.engine.abdullah.installer.InstallStatusReceiver { *; }
-keep class com.fix.engine.abdullah.service.StoreDownloadForegroundService { *; }
-keep class com.fix.engine.abdullah.service.UpdateWorker { *; }

# Retrofit & OkHttp & DoH
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Firebase Realtime Database
-keep class com.google.firebase.database.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}
-dontwarn com.google.firebase.**

# Coroutines & WorkManager
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ViewBinding & ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# Glide & Shimmer & Material 3
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**
-keep class androidx.transition.** { *; }
-keep class com.google.android.material.transition.** { *; }
-dontwarn com.google.android.material.**
-dontnote **
