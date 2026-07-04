package com.nextide.build;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.eclipse.jdt.internal.compiler.batch.Main;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class RealAndroidBuilder {

    // 🟢 Native Library လုဒ်ဆွဲခြင်းအား လုံခြုံစိတ်ချရသော စနစ်သို့ ပြောင်းလဲခြင်း
    private static boolean isNativeLibraryLoaded = false;
    static {
        try {
            System.loadLibrary("aapt2_jni");
            isNativeLibraryLoaded = true;
        } catch (Throwable t) {
            isNativeLibraryLoaded = false;
        }
    }

    private static native int runAapt2Native(String[] args);

    public interface BuildListener {
        void onLog(String message);
        void onSuccess(File dexOrApk);
        void onFailed(String error);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public RealAndroidBuilder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void buildProject(File projectRootDir, BuildListener listener) {
        executor.submit(() -> {
            // 🟢 [အရေးကြီးဆုံးပြင်ဆင်ချက်] Thread ငြိမ်မသွားစေရန် Exception ရော Error ပါ ဖမ်းနိုင်သော Throwable အား သုံးသည်
            try {
                // ၀။ Native Library ရှိမရှိ အရင်ဆုံး စစ်ဆေးခြင်း
                if (!isNativeLibraryLoaded) {
                    emitFailed(listener, "Critical Error: 'libaapt2_jni.so' failed to load inside App Memory. Check your CMake/NDK build.");
                    return;
                }

                File cacheBin = new File(context.getCodeCacheDir(), "build_bin");
                if (cacheBin.exists()) deleteFolder(cacheBin);
                cacheBin.mkdirs();

                File classesDir = new File(cacheBin, "classes");
                classesDir.mkdirs();

                File genDir = new File(cacheBin, "gen");
                genDir.mkdirs();

                File actualProjectDir = projectRootDir;
                File appFolder = new File(projectRootDir, "app");
                if (appFolder.exists() && appFolder.isDirectory()) {
                    actualProjectDir = appFolder;
                }

                // ၁။ Assets ထဲမှ android.jar အား ထုတ်ယူခြင်း
                emitLog(listener, "[1/5] Extracting build libraries...");
                File androidJarFile = new File(cacheBin, "android.jar");
                if (!extractAssetFile("bin/android.jar", androidJarFile)) {
                    emitFailed(listener, "Error: Missing or damaged assets/bin/android.jar");
                    return;
                }

                // ၂။ AAPT2 Resource Compilation & Linking စတင်ခြင်း
                emitLog(listener, "[2/5] Compiling Android resources via Native JNI AAPT2...");
                
                File resDir = new File(actualProjectDir, "src/main/res");
                File manifestFile = new File(actualProjectDir, "src/main/AndroidManifest.xml");
                File resourceAp_ = new File(cacheBin, "resources.ap_");
                File compiledResDir = new File(cacheBin, "compiled_res");
                compiledResDir.mkdirs();

                if (!resDir.exists() || !manifestFile.exists()) {
                    emitFailed(listener, "Build Error: Missing 'src/main/res/' or 'src/main/AndroidManifest.xml'");
                    return;
                }

                boolean compileResSuccess = runAapt2CompileJni(resDir, compiledResDir, listener);
                if (!compileResSuccess) {
                    emitFailed(listener, "AAPT2 Native Resource Compilation Failed.");
                    return;
                }

                boolean aaptSuccess = runAapt2LinkJni(androidJarFile, manifestFile, compiledResDir, genDir, resourceAp_, listener);
                if (!aaptSuccess || !resourceAp_.exists()) {
                    emitFailed(listener, "AAPT2 Native Resource Linking Failed.");
                    return;
                }

                // ၃။ Java Source ဖိုင်များနှင့် R.java ဖိုင်များကို ရှာဖွေစုဆောင်းခြင်း
                emitLog(listener, "[3/5] Scanning project source files...");
                List<File> javaFiles = new ArrayList<>();
                collectFiles(actualProjectDir, ".java", javaFiles);
                collectFiles(genDir, ".java", javaFiles);

                if (javaFiles.isEmpty()) {
                    emitFailed(listener, "Build Error: No .java files found.");
                    return;
                }

                // ၄။ ECJ Compiler ဖြင့် Java ဖိုင်များကို .class သို့ Compile လုပ်ခြင်း
                emitLog(listener, "[4/5] Compiling with ECJ (Eclipse Compiler)...");
                boolean compileSuccess = runEcj(javaFiles, classesDir, androidJarFile, listener);
                if (!compileSuccess) {
                    emitFailed(listener, "ECJ Compilation Failed.");
                    return;
                }

                // ၅။ D8 ဖြင့် .class ဖိုင်များကို classes.dex သို့ ပြောင်းလဲခြင်း
                emitLog(listener, "[5/5] Transforming .class to Android dex via D8...");
                List<File> classFiles = new ArrayList<>();
                collectFiles(classesDir, ".class", classFiles);

                if (classFiles.isEmpty()) {
                    emitFailed(listener, "Error: No compiled .class files found.");
                    return;
                }

                File dexOutput = new File(cacheBin, "classes.dex");
                runD8(classFiles, cacheBin);

                if (!dexOutput.exists()) {
                    emitFailed(listener, "D8 dexing completed but classes.dex was not generated.");
                    return;
                }

                // ၆။ အပြီးသတ် APK Pack လုပ်ခြင်း
                File outputApk = new File(projectRootDir, "output_built.apk");
                packageApk(resourceAp_, dexOutput, outputApk);

                if (outputApk.exists()) {
                    emitLog(listener, "\n[SUCCESS] APK generated successfully inside project directory!");
                    emitLog(listener, "Path: " + outputApk.getAbsolutePath());
                    emitSuccess(listener, outputApk);
                } else {
                    emitFailed(listener, "Failed to package final APK file.");
                }

            } catch (Throwable t) {
                // 🟢 မည်သည့် Error/Crash တက်တက် အေးခဲမသွားစေဘဲ လမ်းကြောင်းရှင်းလင်းစွာ ပြသပေးခြင်း
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                emitFailed(listener, "Critical Runtime Error:\n" + sw.toString());
            }
        });
    }

    private boolean runAapt2CompileJni(File resDir, File outputDir, BuildListener listener) {
        try {
            List<File> allResFiles = new ArrayList<>();
            getAllResourceFiles(resDir, allResFiles);

            for (File resFile : allResFiles) {
                if (resFile.isDirectory() || resFile.getName().startsWith(".")) continue;

                String[] args = new String[] {
                        "aapt2", "compile", "-o", outputDir.getAbsolutePath(), resFile.getAbsolutePath()
                };

                int exitCode = runAapt2Native(args);
                if (exitCode != 0) {
                    emitLog(listener, "[AAPT2 JNI Error] Compile failed for: " + resFile.getName());
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean runAapt2LinkJni(File androidJar, File manifest, File compiledResDir, File genDir, File outputAp_, BuildListener listener) {
        try {
            List<String> argsList = new ArrayList<>();
            argsList.add("aapt2");
            argsList.add("link");
            argsList.add("--manifest");
            argsList.add(manifest.getAbsolutePath());
            argsList.add("-I");
            argsList.add(androidJar.getAbsolutePath());
            argsList.add("--java-output");
            argsList.add(genDir.getAbsolutePath());
            argsList.add("-o");
            argsList.add(outputAp_.getAbsolutePath());

            File[] flatFiles = compiledResDir.listFiles();
            if (flatFiles != null) {
                for (File f : flatFiles) {
                    if (f.isFile() && f.getName().endsWith(".flat")) {
                        argsList.add(f.getAbsolutePath());
                    }
                }
            }

            int exitCode = runAapt2Native(argsList.toArray(new String[0]));
            return exitCode == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean extractAssetFile(String assetPath, File destFile) {
        try (InputStream is = context.getAssets().open(assetPath);
             FileOutputStream os = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int byteRead;
            while ((byteRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, byteRead);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean runEcj(List<File> javaFiles, File outputDir, File androidJar, BuildListener listener) {
        List<String> args = new ArrayList<>();
        args.add("-1.8");
        args.add("-nowarn");
        args.add("-cp");
        args.add(androidJar.getAbsolutePath());
        args.add("-d");
        args.add(outputDir.getAbsolutePath());

        for (File f : javaFiles) {
            args.add(f.getAbsolutePath());
        }

        StringWriter errWriter = new StringWriter();
        PrintWriter err = new PrintWriter(errWriter);

        Main compiler = new Main(new PrintWriter(new StringWriter()), err, false, null, null);
        boolean success = compiler.compile(args.toArray(new String[0]));

        String compileErrors = errWriter.toString();
        if (!compileErrors.isEmpty()) {
            emitLog(listener, compileErrors);
        }
        return success;
    }

    private void runD8(List<File> classFiles, File outputDir) throws Exception {
        List<Path> paths = new ArrayList<>();
        for (File f : classFiles) {
            paths.add(f.toPath());
        }

        D8Command.Builder builder = D8Command.builder()
                .addProgramFiles(paths)
                .setOutput(outputDir.toPath(), OutputMode.DexIndexed)
                .setMinApiLevel(21);

        D8.run(builder.build());
    }

    private void packageApk(File srcApk, File dexFile, File destApk) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(srcApk));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destApk))) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }

            zos.putNextEntry(new ZipEntry("classes.dex"));
            try (FileInputStream fis = new FileInputStream(dexFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    private void collectFiles(File dir, String ext, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) {
                collectFiles(f, ext, out);
            } else if (f.getName().endsWith(ext)) {
                out.add(f);
            }
        }
    }

    private void getAllResourceFiles(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    getAllResourceFiles(f, fileList);
                } else {
                    fileList.add(f);
                }
            }
        }
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolder(f);
                else f.delete();
            }
        }
        folder.delete();
    }

    private void emitLog(BuildListener l, String msg) {
        mainHandler.post(() -> l.onLog(msg + "\n"));
    }

    private void emitSuccess(BuildListener l, File file) {
        mainHandler.post(() -> l.onSuccess(file));
    }

    private void emitFailed(BuildListener l, String err) {
        mainHandler.post(() -> l.onFailed(err));
    }
}
