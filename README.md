# 🚀 Next IDE - On-Device Android Build System

Next IDE သည် Computer (Laptop) သို့မဟုတ် Gradle Daemon များ အသုံးပြုရန်မလိုဘဲ Android ဖုန်းပေါ်တွင်တင် Android Application (.apk) များနှင့် Java Project များကို တိုက်ရိုက် ရေးသားပြီး Build (Compile) လုပ်နိုင်ရန် ဖန်တီးထားသော ခေတ်မှီ On-device IDE တစ်ခု ဖြစ်သည်။

အထူးသဖြင့် Android 11+ (SDK 30+) နှင့် Oppo, Vivo စသည့် ဖုန်းများတွင် ဖြစ်ပွားလေ့ရှိသော `Runtime.getRuntime().exec()` (Shell Restriction) ကန့်သတ်ချက်များကို JNI Layer ဖြင့် အောင်မြင်စွာ ကျော်လွှားနိုင်ရန် တည်ဆောက်ထားသည်။

---

## ✨ Features (အဓိက စွမ်းဆောင်ရည်များ)

* **No-Root Environment Support:** Oppo A17 ကဲ့သို့သော Non-rooted ဖုန်းများတွင်ပါ အလုပ်လုပ်ခြင်း။
* **In-Process AAPT2 Engine:** AAPT2 Resource Linker အား Shell Level မှ မောင်းနှင်ခြင်းမပြုတော့ဘဲ C++ JNI Bridge မှတစ်ဆင့် App Memory ပေါ်တွင် တိုက်ရိုက် (In-Process) Run စေခြင်း။
* **Eclipse Compiler for Java (ECJ):** Java Source ဖိုင်များကို `.class` သို့ အမှားအယွင်းမရှိ Compile ပြုလုပ်ပေးခြင်း။
* **Google D8 Integration:** `.class` ဖိုင်များကို Android Dex Runtime တွင် Run နိုင်မည့် `classes.dex` ဖိုင်သို့ စနစ်တကျ ပြောင်းလဲပေးခြင်း။
* **AI Auto-Fix Ready:** Build Error များ တက်လာပါက Groq API ကို အသုံးပြု၍ ကုဒ်များကို အလိုအလျောက် ပြင်ဆင်ပေးနိုင်ခြင်း။

---

## 🛠️ Build Architecture (တည်ဆောက်ပုံ စနစ်)

Next IDE ၏ Build Pipeline ကို အောက်ပါအတိုင်း အဆင့်ဆင့် အလုပ်လုပ်ရန် စနစ်တကျ ချိတ်ဆက်ထားသည် -

```text
[Java Source & Resources] 
       │
       ├───> (AAPT2 via JNI Bridge) ──────> [compiled_res & R.java]
       │
       ├───> (ECJ Compiler) ──────────────> [.class Files]
       │
       └───> (D8 Transformer) ────────────> [classes.dex]
                                                   │
                                                   ▼
                                           [Final Output .apk]

