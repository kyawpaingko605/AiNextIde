# AiNextIde 🚀

An advanced Android-based Integrated Development Environment (IDE) built for mobile coding, featuring modular project management, a structured file explorer, and a thread-safe compiler engine.

မိုဘိုင်းလ်ဖုန်းများပေါ်တွင် တိုက်ရိုက် Code ရေးသားနိုင်၊ Build လုပ်နိုင်ရန် ရည်ရွယ်၍ တည်ဆောက်ထားသော စွမ်းဆောင်ရည်မြင့် Android-based IDE (Integrated Development Environment) တစ်ခု ဖြစ်ပါသည်။

---

## 📂 Project Structure & Architecture (ပရောဂျက်၏ ပါဝင်ဖွဲ့စည်းပုံ)

### 📁 Java Sources (`app/src/main/java/com/nextide/`)
* **`MainActivity.java`**
  * **EN:** The core controller of the IDE. It coordinates the navigation drawer, editor tabs, build panel, and toggles between the Welcome and IDE screens seamlessly.
  * **MM:** IDE ရဲ့ အဓိက ဦးနှောက်ဖြစ်ပြီး Drawer၊ Editor Tabs၊ Build Panel နဲ့ Welcome/IDE Screen အကူးအပြောင်း လုပ်ဆောင်ချက်အားလုံးကို ဗဟိုမှ ထိန်းချုပ်ပေးပါတယ်။
* **`fragment/FileExplorerFragment.java`**
  * **EN:** Manages and displays the project's file hierarchy using a lifecycle-safe Tree-view structure. It handles file operations like creating, deleting, and renaming.
  * **MM:** ပရောဂျက်အတွင်းရှိ ဖိုင်များနှင့် ဖိုဒါများကို Tree-view စနစ်ဖြင့် Lifecycle-safe ဖြစ်အောင် စနစ်တကျ ထုတ်ပြပေးပြီး ဖိုင်အသစ်ဆောက်ခြင်း၊ ဖျက်ခြင်း၊ နာမည်ပြောင်းခြင်းတို့ကို စီမံပါတယ်။
* **`fragment/EditorFragment.java`**
  * **EN:** Hosts the active code editor workspace powered by Sora Editor to handle text typing, code modifications, and background file saving.
  * **MM:** Sora Editor ကို အခြေခံထားပြီး ကုဒ်များ ရေးသားခြင်း၊ ပြင်ဆင်ခြင်းနှင့် ဖိုင်များကို စက်ထဲသို့ အလိုအလျောက် သိမ်းဆည်းပေးခြင်းတို့ကို လုပ်ဆောင်ပေးသည့် Workspace ဖြစ်သည်။
* **`fragment/BuildLogFragment.java`**
  * **EN:** Displays real-time compilation streams and terminal outputs at the bottom panel during the build process.
  * **MM:** ကုဒ်များကို ကွန်ပိုင်လုပ်နေစဉ် ထွက်ပေါ်လာသည့် Terminal Log များနှင့် Build အခြေအနေကို အောက်ခြေ Panel တည်းကနေ အချိန်နှင့်တပြေးညီ ပြသပေးပါသည်။
* **`build/BuildManager.java`**
  * **EN:** The background compiler engine that executes build tasks asynchronously on a separate thread to ensure the UI remains smooth and fluid.
  * **MM:** ဖုန်းမျက်နှာပြင် မဟန်ဂတ်ဘဲ သွက်လက်နေစေရန်အတွက် နောက်ကွယ် (Background Thread) ကနေ ကုဒ်များကို လှမ်းပြီး Compile လုပ်ပေးရသည့် အဓိက အင်ဂျင်ဖြစ်ပါသည်။
* **`model/` & `util/`**
  * **EN:** Contains core data structures (`Project`, `FileNode`, `BuildResult`) and standard I/O helper utilities (`FileUtils`) for deep file streaming.
  * **MM:** ပရောဂျက်အချက်အလက်၊ ဖိုင်အခြေအနေများနှင့် ဖိုင်များကို ပျက်စီးမှုမရှိစေဘဲ စက်ထဲသို့ ဖတ်/ရေး ပြုလုပ်ပေးသည့် Core Data များနှင့် Helper Utilities များ ဖြစ်သည်။

---

## ⚙️ How It Works / Workflow (အလုပ်လုပ်ပုံ လုပ်ငန်းစဉ်)

### 1. Launch & Project Scaffolding (စတင်ပွင့်လှစ်ခြင်းနှင့် ပရောဂျက်ဖန်တီးခြင်း)
* **English:** On startup, `MainActivity` loads cached data using `FileUtils.loadProjects()`. If no projects exist, it reveals the clean Welcome Screen. Clicking "+ New Project" triggers a background directory creation via `FileUtils.scaffoldProject()` on local storage.
* **မြန်မာ:** အက်ပ်စဖွင့်ချင်း `MainActivity` က ရှိပြီးသား ပရောဂျက်များကို ဒေတာဝင်ဖတ်ပြီး Welcome Screen တွင် ပြသပေးပါသည်။ ပရောဂျက်အသစ်ဆောက်ပါက `FileUtils.scaffoldProject()` ကနေတစ်ဆင့် ဖုန်းထဲတွင် သတ်မှတ်ထားသည့် Directory အလိုက် Folder ဖွဲ့စည်းပုံကို အလိုအလျောက် ဆောက်ပေးပါသည်။

### 2. Workspace & File Navigation (Workspace နှင့် ဖိုင်လမ်းကြောင်းများ)
* **English:** Selecting a project launches `openProject()`, swapping the UI to the IDE layout. `FileExplorerFragment` fetches the hierarchy and draws subfiles dynamically via `FileTreeAdapter`. Long-pressing any file node triggers context dialog options to rename or recursively delete directories without path breaking.
* **မြန်မာ:** ပရောဂျက်တစ်ခုကို ရွေးချယ်လိုက်လျှင် Welcome Screen ကို ပိတ်ပြီး IDE Workspace ကို ဖွင့်ပေးပါသည်။ `FileExplorerFragment` သည် `FileTreeAdapter` ကို သုံး၍ ဖိုင်များကို Tree-view ပုံစံဖြင့် စီစဉ်ထုတ်ပြပေးပြီး ဖိုင်တစ်ခုချင်းစီကို ဖိထားပါက လုံခြုံစိတ်ချရသော ဖိုင်ဖျက်ခြင်း/နာမည်ပြောင်းခြင်း Menu များ ပေါ်လာမည်ဖြစ်သည်။

### 3. Multi-Tab Editing (ကုဒ်တည်းဖြတ်ခြင်းစနစ်)
* **English:** Tapping a file opens it via `openFile()`, inserting a dedicated tab onto the `TabLayout` and embedding an instance of `EditorFragment`. Switching between tabs dynamically swaps the editor pane view layouts while preserving unsaved structural states without leaks.
* **မြန်မာ:** ဖိုင်တစ်ခုကို နှိပ်လိုက်ပါက `TabLayout` တွင် Tab အသစ်တစ်ခုတိုးလာပြီး Sora Editor အခြေခံ `EditorFragment` ကို ဖွင့်ပေးပါသည်။ Tab များ အကူးအပြောင်းလုပ်ရာတွင် Memory Leak မဖြစ်စေဘဲ ပြင်ဆင်နေသည့် ကုဒ်များကို အဆင်သင့် မှတ်သားပေးထားပါသည်။

### 4. Asynchronous Build & Real-time Logs (ကုဒ်စမ်းသပ် Run ခြင်းနှင့် Log ပြသခြင်း)
* **English:** Pressing "Build" auto-commits all open editor modifications to disk. Then, `BuildManager` handles execution on a background thread. Real-time diagnostic stream steps are safely updated onto the Main Thread using `runOnUiThread()` inside `BuildLogFragment` until it returns a definitive status toast.
* **မြန်မာ:** "Build" ခလုတ်ကို နှိပ်လိုက်သည်နှင့် မသိမ်းရသေးသော ကုဒ်ပြင်ဆင်ချက်အားလုံးကို စက်ထဲသို့ အရင်ဆုံး Auto-save လုပ်ပစ်ပါသည်။ ထို့နောက် `BuildManager` က နောက်ကွယ် Thread မှ ကုဒ်များကို Compile လုပ်ပြီး ထွက်လာသမျှ Log များကို `runOnUiThread()` စနစ်ဖြင့် `BuildLogFragment` ဆီသို့ ပေးပို့ကာ အောက်ခြေဘားတွင် စာသားများ တန်းစီထုတ်ပြပေးပါသည်။

---

## 🛠️ Built With (အသုံးပြုထားသော နည်းပညာများ)

* **Language:** Java (Android SDK 26+)
* **UI Architecture:** View Binding, CoordinatorLayout, DrawerLayout, Fragments
* **Core Text Engine:** [Sora Editor](https://github.com/Rosemoe/sora-editor) - High-performance code editor for Android.

