package com.nextide.util;

import android.content.Context;
import com.google.gson.Gson;
import com.nextide.model.Project;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    private static final String PROJECTS_FILE = "projects.json";
    private static final Gson GSON = new Gson();

    // ── Read / Write files (AIDE & Standard IDE Specification) ─────────────────────────────
    public static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    // 🟢 ပြင်ဆင်ချက်: Default empty content ကိုမရေးတော့ အဲဒီအစား အကွံကိုယ်တိုင် content ထည့်သွင်းရန်
    public static void writeFile(File file, String content) throws IOException {
        if (content == null) {
            content = "";
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write(content);
        }
    }

    public static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        return f.delete();
    }

    // ── Project persistence ────────────────────────────────────────────
    public static List<Project> loadProjects(Context ctx) {
        File f = new File(ctx.getFilesDir(), PROJECTS_FILE);
        if (!f.exists()) return new ArrayList<>();
        try {
            String json = readFile(f);
            Project[] arr = GSON.fromJson(json, Project[].class);
            List<Project> list = new ArrayList<>();
            if (arr != null) {
                for (Project p : arr) {
                    if (p.getPath() != null && new File(p.getPath()).exists()) {
                        list.add(p);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveProjects(Context ctx, List<Project> projects) {
        File f = new File(ctx.getFilesDir(), PROJECTS_FILE);
        try {
            writeFile(f, GSON.toJson(projects));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File getProjectsRootDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ── Scaffold new project ───────────────────────────────────────────
    public static void scaffoldProject(File projectDir, String language) throws IOException {
        if (!projectDir.exists()) projectDir.mkdirs();
        
        String lang = (language != null) ? language.toLowerCase().trim() : "text";
        switch (lang) {
            case "java":   scaffoldJava(projectDir);   break;
            case "kotlin": scaffoldKotlin(projectDir); break;
            case "python": scaffoldPython(projectDir); break;
            case "cpp":    scaffoldCpp(projectDir);    break;
            default:       scaffoldText(projectDir);   break;
        }
    }

    // 📱 Android App (Standard Module Base Build) စနစ်အမှန်အတိုင်း ပြင်ဆင်ထားပါသည်
    private static void scaffoldJava(File dir) throws IOException {
        String projectName = dir.getName();
        
        // ၁။ Root Levels အောက်တွင် 'app' Module Folder ကို ခံ၍ တည်ဆောက်ခြင်း
        File appDir = new File(dir, "app");
        File srcDir = new File(appDir, "src/main/java/com/nextide/app");
        File layoutDir = new File(appDir, "src/main/res/layout");
        File valuesDir = new File(appDir, "src/main/res/values");
        
        srcDir.mkdirs();
        layoutDir.mkdirs();
        valuesDir.mkdirs();

        // ၂။ Project Root Level ရှိ ဖိုင်များကို ဆောက်ခြင်း
        // settings.gradle (Root Level)
        writeFile(new File(dir, "settings.gradle"),
            "rootProject.name = '" + projectName + "'\n" +
            "include ':app'\n");

        // build.gradle (Root Level)
        writeFile(new File(dir, "build.gradle"),
            "// Top-level build file where you can add configuration options common to all sub-projects/modules.\n" +
            "plugins {\n" +
            "    id 'com.android.application' version '8.4.2' apply false\n" +
            "}\n");

        // README.md (Root Level)
        writeFile(new File(dir, "README.md"),
            "# " + projectName + "\n\nAn Android Java app project created with Next IDE.\n");


        // ၃။ 'app' Module Folder အောက်ရှိ တကယ့် ဖိုင်များကို ဆောက်ခြင်း
        // app/build.gradle
        writeFile(new File(appDir, "build.gradle"),
            "plugins {\n" +
            "    id 'com.android.application'\n" +
            "}\n\n" +
            "android {\n" +
            "    namespace 'com.nextide.app'\n" +
            "    compileSdk 34\n\n" +
            "    defaultConfig {\n" +
            "        applicationId \"com.nextide.app\"\n" +
            "        minSdk 26\n" +
            "        targetSdk 34\n" +
            "        versionCode 1\n" +
            "        versionName \"1.0\"\n" +
            "        multiDexEnabled true\n" +
            "    }\n\n" +
            "    compileOptions {\n" +
            "        sourceCompatibility JavaVersion.VERSION_17\n" +
            "        targetCompatibility JavaVersion.VERSION_17\n" +
            "    }\n" +
            "}\n\n" +
            "dependencies {\n" +
            "    implementation 'androidx.appcompat:appcompat:1.6.1'\n" +
            "    implementation 'androidx.core:core:1.12.0'\n" +
            "}\n");

        // app/src/main/java/com/nextide/app/MainActivity.java
        writeFile(new File(srcDir, "MainActivity.java"),
            "package com.nextide.app;\n\n" +
            "import android.os.Bundle;\n" +
            "import androidx.appcompat.app.AppCompatActivity;\n" +
            "import android.widget.TextView;\n\n" +
            "public class MainActivity extends AppCompatActivity {\n" +
            "    @Override\n" +
            "    protected void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.activity_main);\n" +
            "    }\n" +
            "}\n");

        // app/src/main/res/layout/activity_main.xml
        writeFile(new File(layoutDir, "activity_main.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    android:orientation=\"vertical\"\n" +
            "    android:gravity=\"center\">\n\n" +
            "    <TextView\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"@string/hello_world\"\n" +
            "        android:textSize=\"20sp\"\n" +
            "        android:textStyle=\"bold\"/>\n\n" +
            "</LinearLayout>\n");

        // app/src/main/res/values/strings.xml
        writeFile(new File(valuesDir, "strings.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources>\n" +
            "    <string name=\"app_name\">" + projectName + "</string>\n" +
            "    <string name=\"hello_world\">Hello, World from NextIDE!</string>\n" +
            "</resources>\n");

        // app/src/main/AndroidManifest.xml
        writeFile(new File(appDir, "src/main/AndroidManifest.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"com.nextide.app\">\n\n" +
            "    <application\n" +
            "        android:label=\"@string/app_name\"\n" +
            "        android:allowBackup=\"true\">\n" +
            "        <activity android:name=\".MainActivity\"\n" +
            "            android:exported=\"true\">\n" +
            "            <intent-filter>\n" +
            "                <action android:name=\"android.intent.action.MAIN\" />\n" +
            "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
            "            </intent-filter>\n" +
            "        </activity>\n" +
            "    </application>\n" +
            "</manifest>\n");
    }

    private static void scaffoldKotlin(File dir) throws IOException {
        File srcDir = new File(dir, "src");
        srcDir.mkdirs();
        writeFile(new File(srcDir, "Main.kt"),
            "fun main() {\n    println(\"Hello, World!\")\n}\n");
        writeFile(new File(dir, "README.md"),
            "# " + dir.getName() + "\n\nA Kotlin project created with Next IDE.\n");
    }

    private static void scaffoldPython(File dir) throws IOException {
        writeFile(new File(dir, "main.py"),
            "def main():\n    print(\"Hello, World!\")\n\nif __name__ == '__main__':\n    main()\n");
        writeFile(new File(dir, "requirements.txt"), "# Add your dependencies here\n");
        writeFile(new File(dir, "README.md"),
            "# " + dir.getName() + "\n\nA Python project created with Next IDE.\n");
    }

    private static void scaffoldCpp(File dir) throws IOException {
        File srcDir = new File(dir, "src");
        srcDir.mkdirs();
        writeFile(new File(srcDir, "main.cpp"),
            "#include <iostream>\n\nint main() {\n    std::cout << \"Hello, World!\" << std::endl;\n    return 0;\n}\n");
        writeFile(new File(dir, "CMakeLists.txt"),
            "cmake_minimum_required(VERSION 3.10)\nproject(" + dir.getName() + ")\nset(CMAKE_CXX_STANDARD 17)\nadd_executable(app src/main.cpp)\n");
        writeFile(new File(dir, "README.md"),
            "# " + dir.getName() + "\n\nA C++ project created with Next IDE.\n");
    }

    private static void scaffoldText(File dir) throws IOException {
        writeFile(new File(dir, "notes.txt"), "# " + dir.getName() + "\n\nStart writing here...\n");
    }

    public static int countLines(File file) {
        if (file.isDirectory()) {
            int total = 0;
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) total += countLines(f);
            }
            return total;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            int lines = 0;
            while (br.readLine() != null) lines++;
            return lines;
        } catch (IOException e) { return 0; }
    }

    // ── 🟢 တိုးချဲ့ထားသော စနစ်သစ်- Create New File / Folder (Inside & Outside Architecture) ──

    /**
     * ရွေးချယ်ထားသော Folder ၏ "အတွင်းဘက်" ၌ File အသစ်ဆောက်ခြင်း
     */
    public static boolean createNewFileInside(File parentFolder, String fileName) throws IOException {
        if (parentFolder == null || !parentFolder.isDirectory() || fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        File newFile = new File(parentFolder, fileName.trim());
        writeFile(newFile, "// New file created by Next IDE\n");
        return newFile.exists();
    }

    /**
     * ရွေးချယ်ထားသော Folder နှင့် တန်းတူ "အပြင်ဘက် Level" ၌ File အသစ်ဆောက်ခြင်း
     */
    public static boolean createNewFileOutside(File currentFolder, String fileName) throws IOException {
        if (currentFolder == null || fileName == null || fileName.trim().isEmpty()) return false;
        File parentOfFolder = currentFolder.getParentFile();
        if (parentOfFolder == null) return false; 
        
        File newFile = new File(parentOfFolder, fileName.trim());
        writeFile(newFile, "// New file created by Next IDE\n");
        return newFile.exists();
    }

    /**
     * ရွေးချယ်ထားသော Folder ၏ "အတွင်းဘက်" ၌ Folder အသစ်ဆောက်ခြင်း
     */
    public static boolean createNewFolderInside(File parentFolder, String folderName) {
        if (parentFolder == null || !parentFolder.isDirectory() || folderName == null || folderName.trim().isEmpty()) {
            return false;
        }
        File newFolder = new File(parentFolder, folderName.trim());
        return newFolder.mkdirs() || newFolder.exists();
    }

    /**
     * ရွေးချယ်ထားသော Folder နှင့် တန်းတူ "အပြင်ဘက် Level" ၌ Folder အသစ်ဆောက်ခြင်း
     */
    public static boolean createNewFolderOutside(File currentFolder, String folderName) {
        if (currentFolder == null || folderName == null || folderName.trim().isEmpty()) return false;
        File parentOfFolder = currentFolder.getParentFile();
        if (parentOfFolder == null) return false;

        File newFolder = new File(parentOfFolder, folderName.trim());
        return newFolder.mkdirs() || newFolder.exists();
    }
}
