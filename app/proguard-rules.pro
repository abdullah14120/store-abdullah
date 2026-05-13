# حفظ أسماء الكلاسات الخاصة بالموديل (البيانات) لضمان عمل Gson
-keepclassmembers class com.fix.engine.abdullah.data.model.** { *; }

# حماية حركات الانتقال (Shared Element Transitions)
-keep class androidx.transition.** { *; }
-keep class com.google.android.material.transition.** { *; }

# حماية مكتبة Glide للأيقونات
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-dontwarn com.bumptech.glide.**

# حماية الـ ViewBinding والـ ViewModel
-keep class * extends androidx.lifecycle.ViewModel
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# حماية مكتبة Retrofit للاتصال بالسيرفر
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
