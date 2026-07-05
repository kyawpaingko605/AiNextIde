#include <jni.h>
#include <vector>
#include <string>
#include <android/log.h>
#include <dlfcn.h> // Dynamic Loading အတွက် ဖြည့်စွက်ချက်

#define LOG_TAG "AAPT2_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Function Pointer အမျိုးအစား သတ်မှတ်ခြင်း
typedef int (*aapt2_main_t)(int, char**);

extern "C" JNIEXPORT jint JNICALL
Java_com_nextide_build_RealAndroidBuilder_runAapt2Native(JNIEnv* env, jclass clazz, jobjectArray args) {
    if (args == nullptr) return -1;

    int argc = env->GetArrayLength(args);
    if (argc == 0) return -1;
    
    // 🟢 [အရေးကြီးဆုံးအပိုင်း] Memory ထဲသို့ Load လုပ်ထားပြီးသား libaapt2.so ထံမှ Symbol ကို ရှာဖွေခြင်း
    void* handle = dlopen("libaapt2.so", RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        // အကယ်၍ ရှာမတွေ့ပါက Local Path အနေဖြင့် ထပ်မံကြိုးစားခြင်း
        handle = dlopen("libaapt2", RTLD_NOW | RTLD_GLOBAL);
    }

    if (!handle) {
        LOGE("Failed to dlopen libaapt2.so: %s", dlerror());
        return -1;
    }

    // aapt2_main function ရဲ့ နေရာကို ရှာဖွေခြင်း
    aapt2_main_t aapt2_main_func = (aapt2_main_t) dlsym(handle, "aapt2_main");
    if (!aapt2_main_func) {
        LOGE("Failed to find symbol 'aapt2_main': %s", dlerror());
        dlclose(handle);
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

    for (int i = 0; i < argc; ++i) {
        argv.push_back(const_cast<char*>(strArgs[i].c_str()));
    }

    LOGI("Executing In-Process AAPT2 via Dynamic Linker...");

    int result = -1;
    try {
        // ရှာဖွေတွေ့ရှိသော ကွက်တိ Function ထဲသို့ Arguments များ ပို့လွှတ်မောင်းနှင်ခြင်း
        result = aapt2_main_func(argc, argv.data());
    } catch (...) {
        LOGE("AAPT2 Native Crash Occurred");
        result = -1;
    }

    for (size_t i = 0; i < jniStrings.size(); ++i) {
        if (jniStrings[i] != nullptr && rawStrings[i] != nullptr) {
            env->ReleaseStringUTFChars(jniStrings[i], rawStrings[i]);
        }
        if (jniStrings[i] != nullptr) {
            env->DeleteLocalRef(jniStrings[i]);
        }
    }

    dlclose(handle);
    return result;
}
