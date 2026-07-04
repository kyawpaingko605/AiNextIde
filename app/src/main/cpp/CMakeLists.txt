cmake_minimum_required(VERSION 3.10.2)
project("ai_nextide_native")

# AAPT2 Core Libraries များကို ဤနေရာတွင် ကြေညာပါ (သင့် Project ထဲရှိ လမ်းကြောင်းအတိုင်း ညှိပေးရန်)
# ဥပမာ - add_library(libaapt2 STATIC IMPORTED)

add_library(aapt2_jni SHARED aapt2_jni.cpp)

target_link_libraries(aapt2_jni
    # libaapt2 core modules များကို ဤနေရာတွင် ချိတ်ဆက်ရမည်
    log
    android
)
