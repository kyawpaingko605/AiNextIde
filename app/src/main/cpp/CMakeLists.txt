cmake_minimum_required(VERSION 3.22.1)
project("nextide_native")

# ၁။ jniLibs ထဲမှာရှိပြီးသား libaapt2.so အား Prebuilt Shared Library အဖြစ် ကြေညာခြင်း
add_library(aapt2_core SHARED IMPORTED)

# ၂။ သင့်ရဲ့ jniLibs Folder လမ်းကြောင်းအတိုက် တိုက်ရိုက်ချိတ်ဆက်ခြင်း
set_target_properties(aapt2_core PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libaapt2.so
)

# ၃။ လက်ရှိ စိတ်ချရအောင် ပြင်ဆင်ပြီးသား JNI Bridge (aapt2_jni.cpp) အား ကြေညာခြင်း
add_library(aapt2_jni SHARED aapt2_jni.cpp)

# ၄။ JNI တံတား၊ AAPT2 Core Engine (.so) နှင့် Android Log Module တို့အား ပေါင်းစည်းခြင်း
target_link_libraries(aapt2_jni
    aapt2_core
    log
    android
)
