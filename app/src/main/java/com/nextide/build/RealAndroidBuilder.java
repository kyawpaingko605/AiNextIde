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

    public void buildProject(File projectRootDir, BuildListener listener) {
        executor.submit(() -> {
            try {
                // Cache ရှင်းလင်းရေးနှင့် Folder များတည်ဆောက်ခြင်း
                File cacheBin = new File(context.getCodeCacheDir(), "build_bin");
                if (cacheBin.exists()) deleteFolder(cacheBin);
                cacheBin.mkdirs();

                File classesDir = new File(cacheBin, "classes");
                classesDir.mkdirs();

                File genDir = new File(cacheBin, "gen");
                genDir.mkdirs();

                // ၁။ Project Directory လမ်းကြောင်း ညှိယူခြင်း
                File actualProjectDir = projectRootDir;
                File appFolder = new File(projectRootDir, "app");
                if (appFolder.exists() && appFolder.isDirectory()) {
                    actualProjectDir = appFolder;
                }

                // ၂။ AAPT2 Tool အား Permission Denied မဖြစ်စေရန် Private Execute Zone သို့ ထုတ်ယူခြင်း
                emitLog(listener, "[1/5] Preparing AAPT2 packaging tool...");
                File aapt2Tool = getAapt2Executable(listener);
                if (aapt2Tool == null) {
                    emitFailed(listener, "Error: Unable to load or prepare Executable AAPT2 Binary.");
                    return;
                }

                // ၃။ Assets ထဲမှ android.jar အား ထုတ်ယူခြင်း
                emitLog(listener, "Preparing android.jar framework library...");
                File androidJarFile = new File(cacheBin, "android.jar");
                if (!extractAssetFile("bin/android.jar", androidJarFile)) {
                    emitFailed(listener, "Error: Missing or damaged assets/bin/android.jar");
                    return;
                }

                // ၄။ AAPT2 Resource Compilation & Linking စတင်ခြင်း
                emitLog(listener, "[2/5] Compiling Android resources via AAPT2...");
                
                File resDir = new File(actualProjectDir, "src/main/res");
                File manifestFile = new File(actualProjectDir, "src/main/AndroidManifest.xml");
                File resourceAp_ = new File(cacheBin, "resources.ap_");
                File compiledResDir = new File(cacheBin, "compiled_res");
                compiledResDir.mkdirs();

                if (!resDir.exists() || !manifestFile.exists()) {
                    emitFailed(listener, "Build Error: Missing 'src/main/res/' or 'src/main/AndroidManifest.xml'");
                    return;
                }

                // အဆင့် (က) - XML Resource များကို တစ်ဖိုင်ချင်း Flat ဖိုင်ပြောင်းခြင်း (Detailed Logging)
                boolean compileResSuccess = runAapt2Compile(aapt2Tool, resDir, compiledResDir, listener);
                if (!compileResSuccess) {
                    emitFailed(listener, "AAPT2 Resource Compilation (Flat files creation) Failed.");
                    return;
                }

                // အဆင့် (ခ) - Flat ဖိုင်များကို စုစည်းချိတ်ဆက်ပြီး R.java နှင့် Resource APK ထုတ်ခြင်း
                boolean aaptSuccess = runAapt2Link(aapt2Tool, androidJarFile, manifestFile, compiledResDir, genDir, resourceAp_, listener);
                if (!aaptSuccess || !resourceAp_.exists()) {
                    emitFailed(listener, "AAPT2 Resource Linking Failed.");
                    return;
                }

                // ၅။ Java Source ဖိုင်များနှင့် R.java ဖိုင်များကို ရှာဖွေစုဆောင်းခြင်း
                emitLog(listener, "[3/5] Scanning project source files...");
                List<File> javaFiles = new ArrayList<>();
                collectFiles(actualProjectDir, ".java", javaFiles);
                collectFiles(genDir, ".java", javaFiles);

                if (javaFiles.isEmpty()) {
                    emitFailed(listener, "Build Error: No .java files found.");
                    return;
                }

                // ၆။ ECJ Compiler ဖြင့် Java ဖိုင်များကို .class သို့ Compile လုပ်ခြင်း
                emitLog(listener, "[4/5] Compiling with ECJ (Eclipse Compiler)...");
                boolean compileSuccess = runEcj(javaFiles, classesDir, androidJarFile, listener);
                if (!compileSuccess) {
                    emitFailed(listener, "ECJ Compilation Failed.");
                    return;
                }

                // ၇။ D8 ဖြင့် .class ဖိုင်များကို classes.dex သို့ ပြောင်းလဲခြင်း
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

                // ၈။ အပြီးသတ် APK Pack လုပ်ခြင်း
                File outputApk = new File(projectRootDir, "output_built.apk");
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
        try {
            File binDir = context.getDir("bin", Context.MODE_PRIVATE);
            File aapt2File = new File(binDir, "aapt2");

            if (aapt2File.exists()) {
                aapt2File.delete(); 
            }

            String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            File libAapt2 = new File(nativeLibDir, "libaapt2.so"); 

            boolean copied = false;
            if (libAapt2.exists()) {
                emitLog(listener, "  -> Found libaapt2.so in nativeLibraryDir, copying...");
                copied = copyFile(libAapt2, aapt2File);
            } else {
                File altAapt2 = new File(nativeLibDir, "aapt2");
                if (altAapt2.exists()) {
                    emitLog(listener, "  -> Found aapt2 in nativeLibraryDir, copying...");
                    copied = copyFile(altAapt2, aapt2File);
                } else {
                    emitLog(listener, "  -> Native binary not found in jniLibs, extracting from assets...");
                    copied = extractAssetFile("bin/aapt2", aapt2File);
                }
            }

            if (!copied) {
                emitLog(listener, "  -> [ERROR] Failed to copy or extract AAPT2 binary.");
                return null;
            }

            aapt2File.setReadable(true, false);
            aapt2File.setExecutable(true, false);

            Process chmodProc = Runtime.getRuntime().exec(new String[]{"chmod", "755", aapt2File.getAbsolutePath()});
            chmodProc.waitFor();

            if (aapt2File.canExecute()) {
                emitLog(listener, "  -> AAPT2 Executable successfully initialized.");
                return aapt2File;
            } else {
                emitLog(listener, "  -> [ERROR] Target binary is still not executable.");
            }
        } catch (Exception e) {
            emitLog(listener, "  -> Executable Exception: " + e.getMessage());
        }
        return null;
    }

    // 🟢 အဓိကပြင်ဆင်ချက်- Resource Error များကို အသေးစိတ် ဖမ်းယူပြသမည့် စနစ်
    private boolean runAapt2Compile(File aapt2, File resDir, File outputDir, BuildListener listener) {
        try {
            List<File> allResFiles = new ArrayList<>();
            getAllResourceFiles(resDir, allResFiles);

            if (allResFiles.isEmpty()) return true; 

            for (File resFile : allResFiles) {
                if (resFile.isDirectory() || resFile.getName().startsWith(".")) continue;

                List<String> command = new ArrayList<>();
                command.add(aapt2.getAbsolutePath());
                command.add("compile");
                command.add("-o");
                command.add(outputDir.getAbsolutePath());
                command.add(resFile.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true); // Error messages များကိုပါ တစ်ပါတည်း ဖတ်ရှုရန်
                Process p = pb.start();

                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    emitLog(listener, "[AAPT2 Output] " + line); // တကယ့် XML/Resource အမှားကို တန်းပြပါမည်
                }
                
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    emitLog(listener, "[ERROR] AAPT2 Compile Failed for file: " + resFile.getName() + " (Exit Code: " + exitCode + ")");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            emitLog(listener, "AAPT2 Compile Exception: " + e.getMessage());
            return false;
        }
    }

    private boolean runAapt2Link(File aapt2, File androidJar, File manifest, File compiledResDir, File genDir, File outputAp_, BuildListener listener) {
        try {
            List<String> command = new ArrayList<>();
            command.add(aapt2.getAbsolutePath());
            command.add("link");
            command.add("--manifest");
            command.add(manifest.getAbsolutePath());
            command.add("-I");
            command.add(androidJar.getAbsolutePath());
            
            command.add("--java-output"); 
            command.add(genDir.getAbsolutePath());
            
            command.add("-o");
            command.add(outputAp_.getAbsolutePath());

            File[] flatFiles = compiledResDir.listFiles();
            if (flatFiles != null) {
                for (File f : flatFiles) {
                    if (f.isFile() && f.getName().endsWith(".flat")) {
                        command.add(f.getAbsolutePath());
                    }
                }
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            boolean hasError = false;
            while ((line = br.readLine()) != null) {
                emitLog(listener, "AAPT2 Link: " + line);
                if (line.toLowerCase().contains("error:")) {
                    hasError = true;
                }
            }
            return p.waitFor() == 0 && !hasError;
        } catch (Exception e) {
            emitLog(listener, "AAPT2 Link Exception: " + e.getMessage());
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
