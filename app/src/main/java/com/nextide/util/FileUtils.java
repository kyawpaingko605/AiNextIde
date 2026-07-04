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
        // 🟢 ပြင်ဆင်ချက်: မြန်မာစာနှင့် အခြားဘာသာစကားများ Encoding မပျက်စေရန် UTF-8 ဖြင့် တိကျစွာဖတ်ပါသည်
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public static void writeFile(File file) throws IOException {
        writeFile(file, "");
    }

    public static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        // 🟢 ပြင်ဆင်ချက်: ကုဒ်နှင့် Comment များထဲမှ စာသားများ မပျက်စီးစေရန် UTF-8 ဖြင့် စနစ်တကျ ရေးသားပါသည်
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

    // 📱 Android App (APK Build) အတွက် သီးသန့်တည်ဆောက်ပုံစနစ်သစ်
    private static void scaffoldJava(File dir) throws IOException {
        String projectName = dir.getName();
        
        File srcDir = new File(dir, "src/main/java/com/nextide/app");
        File layoutDir = new File(dir, "src/main/res/layout");
        File valuesDir = new File(dir, "src/main/res/values");
        
        srcDir.mkdirs();
        layoutDir.mkdirs();
        valuesDir.mkdirs();

        // 🟢 ပြင်ဆင်ချက်: R file အား တန်းသိစေရန် import ကွန်မန့်အား ဖြည့်စွက်ပေးထားပြီး အလိုအလျောက် Build ဖြစ်စေပါသည်
        writeFile(new File(srcDir, "MainActivity.java"),
            "package com.nextide.app;\n\n" +
            "import android.os.Bundle;\n" +
            "import android.app.Activity;\n" +
            "import android.widget.TextView;\n" +
            "import com.nextide.app.R;\n\n" + // 👈 ကွက်တိထည့်သွင်းပေးလိုက်ပါပြီ
            "public class MainActivity extends Activity {\n" +
            "    @Override\n" +
            "    protected void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.activity_main);\n" +
            "    }\n" +
            "}\n");

        // 3. activity_main.xml
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

        // 4. strings.xml
        writeFile(new File(valuesDir, "strings.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources>\n" +
            "    <string name=\"app_name\">" + projectName + "</string>\n" +
            "    <string name=\"hello_world\">Hello, World from NextIDE!</string>\n" +
            "</resources>\n");

        // 5. AndroidManifest.xml
        writeFile(new File(dir, "src/main/AndroidManifest.xml"),
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

        // 6. build.gradle
        writeFile(new File(dir, "build.gradle"),
            "plugins {\n" +
            "    id 'com.android.application'\n" +
            "}\n\n" +
            "android {\n" +
            "    compileSdk 34\n\n" +
            "    defaultConfig {\n" +
            "        applicationId \"com.nextide.app\"\n" +
            "        minSdk 26\n" +
            "        targetSdk 34\n" +
            "        versionCode 1\n" +
            "        versionName \"1.0\"\n" +
            "    }\n" +
            "}\n");

        // 7. README.md
        writeFile(new File(dir, "README.md"),
            "# " + projectName + "\n\nAn Android Java app project created with Next IDE.\n");
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
        // 🟢 ပြင်ဆင်ချက်: Line Count တွက်ရာတွင်လည်း Encoding ပျက်ပြီး စာကြောင်းရေလွဲခြင်းမှ ကာကွယ်ရန် ညှိထားပါသည်
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            int lines = 0;
            while (br.readLine() != null) lines++;
            return lines;
        } catch (IOException e) { return 0; }
    }
}
