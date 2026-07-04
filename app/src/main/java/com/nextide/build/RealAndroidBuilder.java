package com.nextide.build;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.eclipse.jdt.internal.compiler.batch.Main;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

                // အဆင့် ၁ - AAPT2 ကို ရှာဖွေခြင်း သို့မဟုတ် ထုတ်ယူခြင်း
                emitLog(listener, "[1/4] Preparing AAPT2 packaging tool...");
                File aapt2Tool = getAapt2Executable(listener);
                if (aapt2Tool == null) {
                    emitFailed(listener, "Error: Unable to load AAPT2 Binary for this device.");
                    return;
                }

                // အဆင့် ၂ - Java ဖိုင်များကို ရှာဖွေခြင်း
                emitLog(listener, "[2/4] Scanning project source files...");
                List<File> javaFiles = new ArrayList<>();
                collectFiles(projectDir, ".java", javaFiles);

                if (javaFiles.isEmpty()) {
                    emitFailed(listener, "Build Error: No .java files found in " + projectDir.getAbsolutePath());
                    return;
                }

                // အဆင့် ၃ - ECJ ဖြင့် .class သို့ Compile လုပ်ခြင်း
                emitLog(listener, "[3/4] Compiling with ECJ (Eclipse Compiler)...");
                boolean compileSuccess = runEcj(javaFiles, classesDir, listener);
                
                if (!compileSuccess) {
                    emitFailed(listener, "ECJ Compilation Failed. Please check syntax errors above.");
                    return;
                }

                // အဆင့် ၄ - D8 ဖြင့် classes.dex သို့ ပြောင်းလဲခြင်း
                emitLog(listener, "[4/4] Transforming .class to Android dex via D8...");
                List<File> classFiles = new ArrayList<>();
                collectFiles(classesDir, ".class", classFiles);

                if (classFiles.isEmpty()) {
                    emitFailed(listener, "Error: No compiled .class files found.");
                    return;
                }

                File dexOutput = new File(cacheBin, "classes.dex");
                runD8(classFiles, cacheBin);

                if (dexOutput.exists()) {
                    emitLog(listener, "\n[SUCCESS] classes.dex generated successfully inside App Sandbox!");
                    emitSuccess(listener, dexOutput);
                } else {
                    emitFailed(listener, "D8 dexing completed but classes.dex was not generated.");
                }

            } catch (Exception e) {
                emitFailed(listener, "Critical Android 11+ Build Failure: " + e.getMessage());
            }
        });
    }

    private File getAapt2Executable(BuildListener listener) {
        // ၁။ 64-bit သို့မဟုတ် 32-bit ဖုန်းအလိုက် OS က သွင်းပေးထားတဲ့ Native Lib ထဲမှာ အရင်ရှာပါမယ် (Android 11+ ကာကွယ်ရေး)
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        File libAapt2 = new File(nativeLibDir, "libaapt2.so");

        if (libAapt2.exists() && libAapt2.canExecute()) {
            emitLog(listener, "  -> Using Native Lib AAPT2 (32/64-bit Safe Mode)");
            return libAapt2;
        }

        // ၂။ Fallback - Native Lib အဆင်မပြေပါက Assets ထဲက aapt2 ကို သုံးပါမည်
        emitLog(listener, "  -> Native AAPT2 not available. Falling back to Assets...");
        File cacheBin = new File(context.getCodeCacheDir(), "build_bin");
        File assetsAapt2 = new File(cacheBin, "aapt2");

        if (extractAssetFile("bin/aapt2", assetsAapt2)) {
            assetsAapt2.setExecutable(true, false);
            return assetsAapt2;
        }

        return null;
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

    private boolean runEcj(List<File> javaFiles, File outputDir, BuildListener listener) {
        List<String> args = new ArrayList<>();
        args.add("-1.8");
        args.add("-nowarn");
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
                .setMinApiLevel(30);

        D8.run(builder.build());
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
