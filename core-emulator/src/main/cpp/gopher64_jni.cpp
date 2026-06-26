#include <jni.h>
#include <android/log.h>

#define LOG_TAG "Gopher64JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_emulator_EmulatorCore_initialize(JNIEnv* env, jobject thiz) {
    LOGI("Gopher64 Core Initialized");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_emulator_EmulatorCore_loadRom(JNIEnv* env, jobject thiz, jstring romPath) {
    const char* path = env->GetStringUTFChars(romPath, NULL);
    LOGI("Gopher64 Core Loaded ROM: %s", path);
    env->ReleaseStringUTFChars(romPath, path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_emulator_EmulatorCore_stepFrame(JNIEnv* env, jobject thiz) {
    // No-op mock frame step
}

JNIEXPORT void JNICALL
Java_com_example_emulator_EmulatorCore_setInputState(JNIEnv* env, jobject thiz, jint port, jint device, jint index, jint id, jshort value) {
    // No-op mock input state
}

JNIEXPORT void JNICALL
Java_com_example_emulator_EmulatorCore_reset(JNIEnv* env, jobject thiz) {
    LOGI("Gopher64 Core Reset");
}

JNIEXPORT void JNICALL
Java_com_example_emulator_EmulatorCore_shutdown(JNIEnv* env, jobject thiz) {
    LOGI("Gopher64 Core Shutdown");
}

}
