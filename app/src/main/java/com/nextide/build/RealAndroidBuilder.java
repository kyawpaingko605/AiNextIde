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

    public void buildProject(File projectDir, BuildListener listener) {
        executor.submit(() -> {
            try {
                File cacheBin = new File(context.getCodeCacheDir(), "build_bin");
                if (cacheBin.exists()) deleteFolder(cacheBin);
                cacheBin.mkdirs();

                File classesDir = new File(cacheBin, "classes");
                classesDir.mkdirs();

                File genDir = new File(cacheBin, "gen");
                genDir.mkdirs();

                // ၁။ jniLibs ထဲက 32/64-bit Native AAPT2 ကို ဆွဲထုတ်ပြင်ဆင်ခြင်း
                emitLog(listener, "[1/5] Preparing AAPT2 packaging tool from jniLibs...");
                File aapt2Tool = getAapt2Executable(listener);
                if (aapt2Tool == null) {
                    emitFailed(listener, "Error: Unable to load Native AAPT2 Binary from jniLibs.");
                    return;
                }

                // 🟢 ၂။ assets/bin/android.jar အား ECJ တွင် Classpath အဖြစ်သုံးရန် Sandbox ထဲသို့ ကြိုတင်ထုတ်ယူခြင်း
                emitLog(listener, "Preparing android.jar framework library...");
                File androidJarFile = new File(cacheBin, "android.jar");
                if (!extractAssetFile("bin/android.jar", androidJarFile)) {
                    emitFailed(listener, "Error: Missing or damaged assets/bin/android.jar");
                    return;
                }

                // ၃။ AAPT2 Link ဖြင့် XML များကို Compile လုပ်ပြီး R.java ထုတ်ခြင်း
                emitLog(listener, "[2/5] Compiling Android resources via AAPT2...");
                File resDir = new File(projectDir, "res");
                File manifestFile = new File(projectDir, "AndroidManifest.xml");
                File resourceAp_ = new File(cacheBin, "resources.ap_");

                if (!resDir.exists() || !manifestFile.exists()) {
                    emitFailed(listener, "Build Error: Missing res/ or AndroidManifest.xml");
                    return;
                }

                boolean aaptSuccess = runAapt2Link(aapt2Tool, androidJarFile, manifestFile, resDir, genDir, resourceAp_, listener);
                if (!aaptSuccess || !resourceAp_.exists()) {
                    emitFailed(listener, "AAPT2 Resource Linking Failed.");
                    return;
                }

                // ၄။ Java Source ဖိုင်များနှင့် R.java ကို ရှာဖွေခြင်း
                emitLog(listener, "[3/5] Scanning project source files...");
                List<File> javaFiles = new ArrayList<>();
                collectFiles(projectDir, ".java", javaFiles);
                collectFiles(genDir, ".java", javaFiles);

                if (javaFiles.isEmpty()) {
                    emitFailed(listener, "Build Error: No .java files found.");
                    return;
                }

                // ၅။ ECJ ဖြင့် Java ဖိုင်များကို .class သို့ Compile လုပ်ခြင်း (`android.jar` အသုံးပြုမည်)
                emitLog(listener, "[4/5] Compiling with ECJ (Eclipse Compiler)...");
                boolean compileSuccess = runEcj(javaFiles, classesDir, androidJarFile, listener);
                
                if (!compileSuccess) {
                    emitFailed(listener, "ECJ Compilation Failed.");
                    return;
                }

                // ၆။ D8 ဖြင့် .class များကို classes.dex ပြောင်းလဲခြင်း
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

                // ၇။ အပြီးသတ် APK အဖြစ် Pack လုပ်ခြင်း
                File outputApk = new File(projectDir, "output_built.apk");
                packageApk(resourceAp_, dexOutput, outputApk);

                if (outputApk.exists()) {
                    emitLog(listener, "\n[SUCCESS] APK generated successfully inside project directory!");
                    emitLog(listener, "Path: " + outputApk.getAbsolutePath());
                    emitSuccess(listener, outputApk);
                } else {
                    emitFailed(listener, "Failed to package final APK file.");
                }

            } catch (Exception e) {
                emitFailed(listener, "Critical Build Failure: " + e.getMessage());
            }
        });
    }

    private File getAapt2Executable(BuildListener listener) {
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        File libAapt2 = new File(nativeLibDir, "libaapt2.so");

        if (libAapt2.exists()) {
            File cacheBin = new File(context.getCodeCacheDir(), "build_bin");
            File fallbackFile = new File(cacheBin, "aapt2_jni");
            if (copyFile(libAapt2, fallbackFile)) {
                fallbackFile.setExecutable(true, false);
                emitLog(listener, "  -> Native AAPT2 prepared safely from jniLibs.");
                return fallbackFile;
            }
        }
        return null;
    }

    // 🟢 ပြင်ဆင်ချက်: link ချိတ်ရာတွင် context sourceDir အစား အသစ်ထုတ်ယူထားသော androidJarFile ကို တိကျစွာသုံးထားပါသည်
    private boolean runAapt2Link(File aapt2, File androidJar, File manifest, File resDir, File genDir, File outputAp_, BuildListener listener) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    aapt2.getAbsolutePath(),
                    "link",
                    "--manifest", manifest.getAbsolutePath(),
                    "-I", androidJar.getAbsolutePath(), 
                    "--src", genDir.getAbsolutePath(),
                    "-o", outputAp_.getAbsolutePath(),
                    "--v2"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                emitLog(listener, "AAPT2: " + line);
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            emitLog(listener, "AAPT2 Exception: " + e.getMessage());
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

    private boolean copyFile(File src, File dest) {
        try (InputStream is = new FileInputStream(src);
             FileOutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 🟢 ပြင်ဆင်ချက်: Classpath နေရာတွင် assets မှ ထုတ်ယူထားသော androidJar ရဲ့ absolute path ကို ထည့်သွင်းပေးလိုက်ပါသည်
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
