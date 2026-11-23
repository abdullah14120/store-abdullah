#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include <android/log.h>
#include <string.h> // من أجل استخدام دالة memset

#define LOG_TAG "SecurityCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 🔒 البصمة الرسمية مصفوفة كأرقام هكس (Hex) مباشرة بدلاً من نصوص واضحة يصعب فحصها ثنائياً
const std::vector<uint8_t> TARGET_SIGNATURE_BYTES = {
    0xA3, 0x45, 0x9B, 0x83, 0xCC, 0x90, 0xAB, 0x39, 0xAF, 0xA5, 0xE3, 0xF8, 0x01, 0x51, 0xAC, 0xD1,
    0x4F, 0x2D, 0x7A, 0x4C, 0xB9, 0x76, 0x74, 0x0C, 0x6C, 0xA4, 0x19, 0x72, 0x33, 0x7C, 0xB7, 0x47
};

extern "C"
JNIEXPORT void JNICALL
Java_com_fix_engine_abdullah_MainActivity_verifySignatureNative(JNIEnv *env, jobject thiz, jobject context) {
    
    // 1. الوصول إلى PackageManager
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);

    // 2. الحصول على اسم الحزمة (Package Name)
    jmethodID getPackageNameMethod = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring packageName = (jstring) env->CallObjectMethod(context, getPackageNameMethod);

    // 3. استدعاء getPackageInfo مع الـ Flags المناسبة (64 لـ GET_SIGNATURES تعميماً للتوافق)
    jclass pmClass = env->GetObjectClass(packageManager);
    jmethodID getPackageInfoMethod = env->GetMethodID(pmClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    
    // Flag 64 = PackageManager.GET_SIGNATURES
    jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 64);

    jclass piClass = env->GetObjectClass(packageInfo);
    
    // 🛠️ تم التصحيح هنا: jfieldID بدلاً من jfieldFieldID
    jfieldID signaturesField = env->GetFieldID(piClass, "signatures", "[Landroid/content/pm/Signature;");
    jobjectArray signaturesArray = (jobjectArray) env->GetObjectField(packageInfo, signaturesField);

    if (signaturesArray == nullptr || env->GetArrayLength(signaturesArray) == 0) {
        // في حال عدم وجود توقيع (محاولة تلاعب خبيثة)، اغلق التطبيق فوراً وبصمت
        _exit(0); 
    }

    // 4. استخراج أول توقيع وتحويله إلى مصفوفة بايتات لعمل الـ Hash
    jobject signatureObj = env->GetObjectArrayElement(signaturesArray, 0);
    jclass signatureClass = env->GetObjectClass(signatureObj);
    jmethodID toByteArrayMethod = env->GetMethodID(signatureClass, "toByteArray", "()[B");
    jbyteArray signatureBytes = (jbyteArray) env->CallObjectMethod(signatureObj, toByteArrayMethod);

    // 5. استدعاء MessageDigest عبر JNI لحساب SHA-256
    jclass digestClass = env->FindClass("java/security/MessageDigest");
    jmethodID getInstanceMethod = env->GetStaticMethodID(digestClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jobject digestObj = env->CallStaticObjectMethod(digestClass, getInstanceMethod, env->NewStringUTF("SHA-256"));

    jmethodID digestMethod = env->GetMethodID(digestClass, "digest", "([B)[B");
    jbyteArray computedHashBytes = (jbyteArray) env->CallObjectMethod(digestObj, digestMethod, signatureBytes);

    // 6. مقارنة الـ Hash الناتج بالبصمة المستهدفة
    jbyte* buffer = env->GetByteArrayElements(computedHashBytes, nullptr);
    jsize length = env->GetArrayLength(computedHashBytes);

    bool isMatch = true;
    if (length != TARGET_SIGNATURE_BYTES.size()) {
        isMatch = false;
    } else {
        for (int i = 0; i < length; i++) {
            if ((uint8_t)buffer[i] != TARGET_SIGNATURE_BYTES[i]) {
                isMatch = false;
                break;
            }
        }
    }

    env->ReleaseByteArrayElements(computedHashBytes, buffer, JNI_ABORT);

    // 🚨 النتيجة الحازمة: إذا لم تتطابق البصمة، يتم قتل التطبيق فوراً من جذوره بنظام الـ Kernel
    if (!isMatch) {
        LOGI("🚨 Signature mismatch detected! Terminating process natively.");
        // استخدام trap يجعل من المستحيل عمل Hook تقليدي للالتفاف على القرار
        __builtin_trap(); 
    }
}

// 🌐 دالة إرجاع رابط المستودع (apps.json) مشفراً
extern "C"
JNIEXPORT jstring JNICALL
Java_com_fix_engine_abdullah_MainActivity_getSecureRepoUrl(JNIEnv *env, jobject thiz) {
    
    const uint8_t obfuscated_url[] = {
        0x32, 0x2E, 0x2E, 0x2A, 0x29, 0x60, 0x75, 0x75, 0x28, 0x3B, 0x2D, 0x74, 0x3D, 0x33, 0x2E, 0x32, 
        0x2F, 0x38, 0x2F, 0x29, 0x3F, 0x28, 0x39, 0x35, 0x34, 0x2E, 0x3F, 0x34, 0x2E, 0x74, 0x39, 0x35, 
        0x37, 0x75, 0x3B, 0x38, 0x3E, 0x2F, 0x36, 0x36, 0x3B, 0x32, 0x6B, 0x6E, 0x6B, 0x68, 0x6A, 0x75, 
        0x29, 0x2E, 0x35, 0x28, 0x3F, 0x77, 0x3B, 0x38, 0x3E, 0x2F, 0x36, 0x36, 0x3B, 0x32, 0x75, 0x28, 
        0x3F, 0x3C, 0x29, 0x75, 0x32, 0x3F, 0x3B, 0x3E, 0x29, 0x75, 0x37, 0x3B, 0x33, 0x34, 0x75, 0x3B, 
        0x2A, 0x2A, 0x29, 0x74, 0x30, 0x29, 0x35, 0x34
    };
    
    int length = sizeof(obfuscated_url) / sizeof(obfuscated_url[0]);
    char* decrypted_url = new char[length + 1]; 
    
    uint8_t key = 0x5A;
    
    for (int i = 0; i < length; i++) {
        decrypted_url[i] = (char)(obfuscated_url[i] ^ key);
    }
    decrypted_url[length] = '\0'; 

    jstring result = env->NewStringUTF(decrypted_url);
    memset(decrypted_url, 0, length);
    delete[] decrypted_url;
    
    return result;
}

// 🌐 دالة إرجاع رابط قاعدة بيانات Firebase مشفراً
extern "C"
JNIEXPORT jstring JNICALL
Java_com_fix_engine_abdullah_ui_details_AppDetailsActivity_getSecureFirebaseUrl(JNIEnv *env, jobject thiz) {
    
    const uint8_t obfuscated_url[] = {
        0x32, 0x2E, 0x2E, 0x2A, 0x29, 0x60, 0x75, 0x75, 0x3B, 0x38, 0x3E, 0x2F, 0x36, 0x36, 0x3B, 0x32, 
        0x77, 0x29, 0x2E, 0x35, 0x28, 0x3F, 0x77, 0x3B, 0x63, 0x6F, 0x3F, 0x3E, 0x77, 0x3E, 0x3F, 0x3C, 
        0x3B, 0x2F, 0x36, 0x2E, 0x77, 0x28, 0x2E, 0x3E, 0x38, 0x74, 0x3F, 0x2F, 0x28, 0x35, 0x2A, 0x3F, 
        0x77, 0x2D, 0x3F, 0x29, 0x2E, 0x6B, 0x74, 0x3C, 0x33, 0x28, 0x3F, 0x38, 0x3B, 0x29, 0x3F, 0x3E, 
        0x3B, 0x2E, 0x3B, 0x38, 0x3B, 0x29, 0x3F, 0x74, 0x3B, 0x2A, 0x2A
    };
    
    int length = sizeof(obfuscated_url) / sizeof(obfuscated_url[0]);
    char* decrypted_url = new char[length + 1]; 
    
    uint8_t key = 0x5A;
    
    for (int i = 0; i < length; i++) {
        decrypted_url[i] = (char)(obfuscated_url[i] ^ key);
    }
    decrypted_url[length] = '\0'; 

    jstring result = env->NewStringUTF(decrypted_url);
    memset(decrypted_url, 0, length);
    delete[] decrypted_url;
    
    return result;
}
