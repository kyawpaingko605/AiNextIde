#include <jni.h>
#include <vector>
#include <string>
#include <android/log.h>

#define LOG_TAG "AAPT2_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// AAPT2 Source ကုဒ်ထဲက တကယ့် Main Entry Point ကို လှမ်းခေါ်ခြင်း
// (AAPT2 codebase ပေါ်မူတည်၍ သင့်ဘက်မှ အမည်ပြောင်းလဲနိုင်သည်)
extern "C" int aapt2_main(int argc, char** argv);

extern "C" JNIEXPORT jint JNICALL
Java_com_nextide_build_RealAndroidBuilder_runAapt2Native(JNIEnv* env, jclass clazz, jobjectArray args) {
    int argc = env->GetArrayLength(args);
    std::vector<std::string> strArgs;
    std::vector<char*> argv;

    // Java String Array မှ C++ String Vector သို့ ပြောင်းလဲခြင်း
    for (int i = 0; i < argc; ++i) {
        jstring string = (jstring) env->GetObjectArrayElement(args, i);
        const char* rawString = env->GetStringUTFChars(string, nullptr);
        if (rawString != nullptr) {
            strArgs.push_back(rawString);
            env->ReleaseStringUTFChars(string, rawString);
        }
    }

    // char* argv array တည်ဆောက်ခြင်း
    for (int i = 0; i < argc; ++i) {
        argv.push_back(const_cast<char*>(strArgs[i].c_str()));
    }

    LOGI("Executing In-Process AAPT2 via JNI with %d arguments...", argc);

    // AAPT2 Engine အား App လုပ်ငန်းစဉ် (Process) အတွင်း၌တင် တိုက်ရိုက် Run စေခြင်း
    try {
        int result = aapt2_main(argc, argv.data());
        LOGI("AAPT2 Native Finished with Exit Code: %d", result);
        return result;
    } catch (const std::exception& e) {
        LOGE("AAPT2 Native Crash: %s", e.what());
        return -1;
    } catch (...) {
        LOGE("AAPT2 Native Crash: Unknown Exception");
        return -1;
    }
}
