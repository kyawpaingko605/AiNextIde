#include <jni.h>
#include <vector>
#include <string>
#include <android/log.h>

#define LOG_TAG "AAPT2_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// AAPT2 Static သို့မဟုတ် Dynamic Library ဘက်မှ တကယ့် Main Entry Point ကို ချိတ်ဆက်ခြင်း
extern "C" int aapt2_main(int argc, char** argv);

extern "C" JNIEXPORT jint JNICALL
Java_com_nextide_build_RealAndroidBuilder_runAapt2Native(JNIEnv* env, jclass clazz, jobjectArray args) {
    if (args == nullptr) {
        LOGE("Arguments array is null.");
        return -1;
    }

    int argc = env->GetArrayLength(args);
    if (argc == 0) {
        LOGE("Arguments array is empty.");
        return -1;
    }
    
    std::vector<std::string> strArgs;
    std::vector<char*> argv;
    std::vector<jstring> jniStrings;
    std::vector<const char*> rawStrings;

    strArgs.reserve(argc);
    argv.reserve(argc);
    jniStrings.reserve(argc);
    rawStrings.reserve(argc);

    // ၁။ Java String Array မှ C++ String Engine ထဲသို့ စနစ်တကျ ပြောင်းလဲသိမ်းဆည်းခြင်း
    for (int i = 0; i < argc; ++i) {
        jstring string = (jstring) env->GetObjectArrayElement(args, i);
        if (string == nullptr) {
            strArgs.push_back("");
            jniStrings.push_back(nullptr);
            rawStrings.push_back(nullptr);
            continue;
        }

        const char* rawString = env->GetStringUTFChars(string, nullptr);
        if (rawString != nullptr) {
            strArgs.push_back(rawString);
            jniStrings.push_back(string);
            rawStrings.push_back(rawString);
        } else {
            strArgs.push_back("");
            jniStrings.push_back(string);
            rawStrings.push_back(nullptr);
        }
    }

    // ၂။ aapt2_main သို့ ပို့ရန်အတွက် ကွန်ဖလစ်မရှိသော သန့်ရှင်းသည့် char* argv array တည်ဆောက်ခြင်း
    for (int i = 0; i < argc; ++i) {
        argv.push_back(const_cast<char*>(strArgs[i].c_str()));
    }

    LOGI("Executing In-Process AAPT2 via JNI with %d arguments...", argc);

    int result = -1;
    
    // ၃။ Native AAPT2 အား App Process Memory အတွင်း၌တင် တိုက်ရိုက် Run စေခြင်း
    try {
        result = aapt2_main(argc, argv.data());
        LOGI("AAPT2 Native Finished with Exit Code: %d", result);
    } catch (const std::exception& e) {
        LOGE("AAPT2 Native Crash (std::exception): %s", e.what());
        result = -1;
    } catch (...) {
        LOGE("AAPT2 Native Crash: Unknown Exception Occurred");
        result = -1;
    }

    // ၄။ Memory Leak နှင့် Crash ကင်းဝေးစေရန် အသုံးပြုခဲ့သော JNI String Reference များအား ပြန်လည်လွှတ်ပေးခြင်း
    for (size_t i = 0; i < jniStrings.size(); ++i) {
        if (jniStrings[i] != nullptr && rawStrings[i] != nullptr) {
            env->ReleaseStringUTFChars(jniStrings[i], rawStrings[i]);
        }
        if (jniStrings[i] != nullptr) {
            env->DeleteLocalRef(jniStrings[i]);
        }
    }

    return result;
}
