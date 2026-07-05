cmake_minimum_required(VERSION 3.22.1)
project("nextide_native")

# ၁။ JNI Bridge (aapt2_jni.cpp) တစ်ခုတည်းကိုသာ Native Library အဖြစ် ဆောက်ပါမည်
add_library(aapt2_jni SHARED aapt2_jni.cpp)

# ၂။ Link-time တွင် libaapt2.so ကို လုံးဝဝင်မစစ်ခိုင်းတော့ဘဲ Android Core Log နှင့်သာ ချိတ်ဆက်ပါမည်
# (Runtime ရောက်မှသာ Java ဘက်က dlopen/System.load ဖြင့် အစီအစဉ်တကျ လုဒ်ဆွဲမောင်းနှင်ပါမည်)
target_link_libraries(aapt2_jni
    log
    android
)
