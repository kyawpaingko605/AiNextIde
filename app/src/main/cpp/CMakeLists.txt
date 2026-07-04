cmake_minimum_required(VERSION 3.22.1)
project("nextide_native")

# ၁။ jniLibs ထဲက libaapt2.so ကို Prebuilt Shared Library အဖြစ် ကြေညာခြင်း
add_library(aapt2_core SHARED IMPORTED)

# ၂။ jniLibs ရဲ့ လမ်းကြောင်းအတိုင်း တိုက်ရိုက်ညွှန်ပြခြင်း
set_target_properties(aapt2_core PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libaapt2.so
)

# ၃။ ကျွန်ုပ်တို့ဆောက်ထားသော JNI Bridge ဖိုင်အား ကြေညာခြင်း
add_library(aapt2_jni SHARED aapt2_jni.cpp)

# ၄။ Libraries အားလုံးကို ပေါင်းစည်းချိတ်ဆက်ခြင်း
target_link_libraries(aapt2_jni
    aapt2_core
    log
    android
)
