#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "SecurityCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

const std::vector<uint8_t> TARGET_SIGNATURE_BYTES = {
    0xA3, 0x45, 0x9B, 0x83, 0xCC, 0x90, 0xAB, 0x39, 0xAF, 0xA5, 0xE3, 0xF8, 0x01, 0x51, 0xAC, 0xD1,
    0x4F, 0x2D, 0x7A, 0x4C, 0xB9, 0x76, 0x74, 0x0C, 0x6C, 0xA4, 0x19, 0x72, 0x33, 0x7C, 0xB7, 0x47
};

static std::string decryptXorString(const uint8_t* data, size_t length, uint8_t key) {
    std::string result(length, '\0');
    for (size_t i = 0; i < length; ++i) {
        result[i] = static_cast<char>(data[i] ^ key);
    }
    return result;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_fix_engine_abdullah_NativeCoreManager_verifySignatureNative(JNIEnv *env, jclass clazz, jobject context) {
    if (context == nullptr) {
        _exit(0);
    }

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);

    jmethodID getPackageNameMethod = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring packageName = (jstring) env->CallObjectMethod(context, getPackageNameMethod);

    jclass pmClass = env->GetObjectClass(packageManager);
    
    jclass buildVersionClass = env->FindClass("android/os/Build$VERSION");
    jfieldID sdkIntField = env->GetStaticFieldID(buildVersionClass, "SDK_INT", "I");
    jint sdkInt = env->GetStaticIntField(buildVersionClass, sdkIntField);

    jbyteArray signatureBytes = nullptr;

    if (sdkInt >= 28) { // Android 9.0 (Pie) / API 28+
        jmethodID getPackageInfoMethod = env->GetMethodID(pmClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
        jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 0x08000000); // GET_SIGNING_CERTIFICATES

        jclass piClass = env->GetObjectClass(packageInfo);
        jfieldID signingInfoField = env->GetFieldID(piClass, "signingInfo", "Landroid/content/pm/SigningInfo;");
        jobject signingInfo = env->GetObjectField(packageInfo, signingInfoField);

        if (signingInfo == nullptr) _exit(0);

        jclass siClass = env->GetObjectClass(signingInfo);
        jmethodID getApkContentsSignersMethod = env->GetMethodID(siClass, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
        auto signaturesArray = (jobjectArray) env->CallObjectMethod(signingInfo, getApkContentsSignersMethod);

        if (signaturesArray == nullptr || env->GetArrayLength(signaturesArray) == 0) _exit(0);

        jobject signatureObj = env->GetObjectArrayElement(signaturesArray, 0);
        jclass signatureClass = env->GetObjectClass(signatureObj);
        jmethodID toByteArrayMethod = env->GetMethodID(signatureClass, "toByteArray", "()[B");
        signatureBytes = (jbyteArray) env->CallObjectMethod(signatureObj, toByteArrayMethod);
    } else {
        jmethodID getPackageInfoMethod = env->GetMethodID(pmClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
        jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 64); // GET_SIGNATURES Legacy

        jclass piClass = env->GetObjectClass(packageInfo);
        jfieldID signaturesField = env->GetFieldID(piClass, "signatures", "[Landroid/content/pm/Signature;");
        auto signaturesArray = (jobjectArray) env->GetObjectField(packageInfo, signaturesField);

        if (signaturesArray == nullptr || env->GetArrayLength(signaturesArray) == 0) _exit(0);

        jobject signatureObj = env->GetObjectArrayElement(signaturesArray, 0);
        jclass signatureClass = env->GetObjectClass(signatureObj);
        jmethodID toByteArrayMethod = env->GetMethodID(signatureClass, "toByteArray", "()[B");
        signatureBytes = (jbyteArray) env->CallObjectMethod(signatureObj, toByteArrayMethod);
    }

    if (signatureBytes == nullptr) _exit(0);

    jclass digestClass = env->FindClass("java/security/MessageDigest");
    jmethodID getInstanceMethod = env->GetStaticMethodID(digestClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jobject digestObj = env->CallStaticObjectMethod(digestClass, getInstanceMethod, env->NewStringUTF("SHA-256"));

    jmethodID digestMethod = env->GetMethodID(digestClass, "digest", "([B)[B");
    auto computedHashBytes = (jbyteArray) env->CallObjectMethod(digestObj, digestMethod, signatureBytes);

    jbyte* buffer = env->GetByteArrayElements(computedHashBytes, nullptr);
    jsize length = env->GetArrayLength(computedHashBytes);

    bool isMatch = true;
    if (length != TARGET_SIGNATURE_BYTES.size()) {
        isMatch = false;
    } else {
        for (int i = 0; i < length; i++) {
            if (static_cast<uint8_t>(buffer[i]) != TARGET_SIGNATURE_BYTES[i]) {
                isMatch = false;
                break;
            }
        }
    }

    env->ReleaseByteArrayElements(computedHashBytes, buffer, JNI_ABORT);

    if (!isMatch) {
        LOGI("🚨 Native Signature Mismatch: Terminating Process.");
        __builtin_trap();
    }
}

JNIEXPORT jstring JNICALL
Java_com_fix_engine_abdullah_NativeCoreManager_getSecureRepoUrlNative(JNIEnv *env, jclass clazz) {
    const uint8_t obfuscated_url[] = {
        0x32, 0x2E, 0x2E, 0x2A, 0x29, 0x60, 0x75, 0x75, 0x28, 0x3B, 0x2D, 0x74, 0x3D, 0x33, 0x2E, 0x32, 
        0x2F, 0x38, 0x2F, 0x29, 0x3F, 0x28, 0x39, 0x35, 0x34, 0x2E, 0x3F, 0x34, 0x2E, 0x74, 0x39, 0x35, 
        0x37, 0x75, 0x3B, 0x38, 0x3E, 0x2F, 0x36, 0x36, 0x3B, 0x32, 0x6B, 0x6E, 0x6B, 0x68, 0x6A, 0x75, 
        0x29, 0x2E, 0x35, 0x28, 0x3F, 0x77, 0x3B, 0x38, 0x3E, 0x2F, 0x36, 0x36, 0x3B, 0x32, 0x75, 0x28, 
        0x3F, 0x3C, 0x29, 0x75, 0x32, 0x3F, 0x3B, 0x3E, 0x29, 0x75, 0x37, 0x3B, 0x33, 0x34, 0x75, 0x3B, 
        0x2A, 0x2A, 0x29, 0x74, 0x30, 0x29, 0x35, 0x34
    };
    std::string decrypted = decryptXorString(obfuscated_url, sizeof(obfuscated_url), 0x5A);
    return env->NewStringUTF(decrypted.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_fix_engine_abdullah_core_PackageInstallerCore_getSecureFirebaseUrl(JNIEnv *env, jclass clazz) {
    const uint8_t obfuscated_url[] = {
        0x32, 0x2E, 0x2E, 0x2A, 0x29, 0x60, 0x75, 0x75, 0x3B, 0x38, 0x3E, 0x2F, 0x36, 0x36, 0x3B, 0x32, 
        0x77, 0x29, 0x2E, 0x35, 0x28, 0x3F, 0x77, 0x3B, 0x63, 0x6F, 0x3F, 0x3E, 0x77, 0x3E, 0x3F, 0x3C, 
        0x3B, 0x2F, 0x36, 0x2E, 0x77, 0x28, 0x2E, 0x3E, 0x38, 0x74, 0x3F, 0x2F, 0x28, 0x35, 0x2A, 0x3F, 
        0x77, 0x2D, 0x3F, 0x29, 0x2E, 0x6B, 0x74, 0x3C, 0x33, 0x28, 0x3F, 0x38, 0x3B, 0x29, 0x3F, 0x3E, 
        0x3B, 0x2E, 0x3B, 0x38, 0x3B, 0x29, 0x3F, 0x74, 0x3B, 0x2A, 0x2A
    };
    std::string decrypted = decryptXorString(obfuscated_url, sizeof(obfuscated_url), 0x5A);
    return env->NewStringUTF(decrypted.c_str());
}

}
