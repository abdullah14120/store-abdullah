# ====================================================================
# Developed by: Abdullah Al-Tamimi
# Project: متجر Abdullah - Official R8/ProGuard Production Rules
# Architecture: Core Logic vs UI Presentation Separation
# ====================================================================

# --------------------------------------------------------------------
# [0] GLOBAL RULES: Annotations & Enums (🚨 CRITICAL)
# --------------------------------------------------------------------
# حماية الـ Annotations لضمان عمل Retrofit و Gson و Room
-keepattributes *Annotation*

# حماية دوال الـ Enums الأساسية لمنع الانهيار عند المقارنة (مثل AppInstallStatus)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --------------------------------------------------------------------
# [1] CORE LOGIC: Security & JNI Bindings
# --------------------------------------------------------------------
# 🛡️ حماية دوال الـ C++ (Native) لضمان عدم كسر ربط JNI أثناء الـ Obfuscation
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.fix.engine.abdullah.MainActivity {
    private native void verifySignatureNative(android.content.Context);
    private native java.lang.String getSecureRepoUrl();
}
-keep class com.fix.engine.abdullah.ui.details.AppDetailsActivity {
    private native java.lang.String getSecureFirebaseUrl();
}

# --------------------------------------------------------------------
# [2] CORE LOGIC: Data Layer & Serialization (Models, Gson, Parcelize)
# --------------------------------------------------------------------
# 🚨 حماية الموديلات لمنع فشل تعيين الـ JSON
-keep class com.fix.engine.abdullah.data.model.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.** { *; }
-dontwarn com.google.gson.**

# حماية واجهات Parcelize لنقل البيانات بين الـ Activities
-keepnames class * extends android.os.Parcelable
-keepclassmembers class * extends android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# --------------------------------------------------------------------
# [3] CORE LOGIC: Networking, Downloading & Realtime DB
# --------------------------------------------------------------------
# 🌐 Retrofit & OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# 🔥 Firebase Realtime Database
-keep class com.google.firebase.database.** { *; }
-dontwarn com.google.firebase.**

# 🚀 Fetch2 Download Engine (Core, UI, and OkHttp modules)
-keep class com.tonyodev.fetch2.** { *; }
-keep class com.tonyodev.fetch2core.** { *; }
-keep class com.tonyodev.fetch2okhttp.** { *; }
-dontwarn com.tonyodev.**

# --------------------------------------------------------------------
# [4] CORE LOGIC: Background Processing & Local DB (Coroutines, Room, WorkManager)
# --------------------------------------------------------------------
# ⚡ Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# 🗄️ Room Database (Required internally by Fetch2)
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# 📦 WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# --------------------------------------------------------------------
# [5] UI / PRESENTATION: Architecture Components
# --------------------------------------------------------------------
# 🎨 ViewBinding & ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# --------------------------------------------------------------------
# [6] UI / PRESENTATION: Media & Transitions
# --------------------------------------------------------------------
# 📸 Glide Image Engine
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# 🌟 Material 3 & Transitions
-keep class androidx.transition.** { *; }
-keep class com.google.android.material.transition.** { *; }
-dontwarn com.google.android.material.**

# --------------------------------------------------------------------
# [7] SYSTEM: Optimization & Traceability
# --------------------------------------------------------------------
# 🛠️ الاحتفاظ ببيانات تتبع الأخطاء المدمجة (Crashlytics Tracking) لتسهيل قراءة الـ Logs
-keepattributes SourceFile, LineNumberTable
-dontnote **
