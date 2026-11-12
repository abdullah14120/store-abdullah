# ===================================================================
# 🛡️ قواعد ProGuard / R8 المتوافقة مع معايير Google Play Protect
# تحمي الكود وتمنع التصنيف الخاطئ (False Positive) كـ Dropper
# ===================================================================

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# الحفاظ على هيكل حزم المتجر الطبيعي لتفادي حساسية الفاحص السلوكي لـ Play Protect
-keeppackagenames com.fix.engine.abdullah.**

# الحفاظ على دوال الـ JNI النيتف
-keepclasseswithmembernames class * {
    native <methods>;
}

# الحفاظ على موديلات البيانات ومستقبلات النظام
-keep class com.fix.engine.abdullah.data.model.** { *; }
-keep class com.fix.engine.abdullah.installer.InstallStatusReceiver { *; }

# قواعد الحفاظ القياسية لمكتبات Firebase و Fetch2 و Glide
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}
-dontwarn okio.**
-dontwarn javax.annotation.**
