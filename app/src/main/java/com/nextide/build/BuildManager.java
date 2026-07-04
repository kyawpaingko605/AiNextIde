package com.nextide.build;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.nextide.model.BuildResult;
import com.nextide.model.Project;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuildManager {
    public interface BuildListener {
        void onLogAppended(String line);
        void onBuildFinished(BuildResult result);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BuildResult triggerBuild(Context context, Project project, BuildListener listener) {
        BuildResult result = new BuildResult();
        result.setStatus(BuildResult.Status.RUNNING);
        result.setStartTime(System.currentTimeMillis());

        executor.submit(() -> {
            try {
                String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                emit(listener, "╔══════════════════════════════════════╗\n");
                emit(listener, "║      Next IDE Build System v1.0      ║\n");
                emit(listener, "╚══════════════════════════════════════╝\n\n");
                emit(listener, "[" + ts + "] REAL COMPILATION STARTED\n");
                emit(listener, "[" + ts + "] Project: " + project.getName() + "\n");
                emit(listener, "[" + ts + "] Target: Android Application (.apk)\n\n");

                // Real Compiler ကို ခေါ်ယူပြီး Log များကို တိုက်ရိုက် ချိတ်ဆက်ပြသခြင်း
                RealAndroidBuilder builder = new RealAndroidBuilder(context);
                
                builder.buildProject(project.getDirectory(), new RealAndroidBuilder.BuildListener() {
                    @Override
                    public void onLog(String message) {
                        // 🟢 AAPT2, ECJ, D8 တို့မှ ထွက်လာသမျှ Log အစစ်အမှန်များကို UI သို့ တိုက်ရိုက် ပို့ပေးပါသည်
                        emit(listener, message);
                    }

                    @Override
                    public void onSuccess(File apkFile) {
                        String endTs = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        double dur = (System.currentTimeMillis() - result.getStartTime()) / 1000.0;
                        
                        emit(listener, "\n[" + endTs + "] Output Path: " + apkFile.getAbsolutePath() + "\n");
                        emit(listener, String.format("[" + endTs + "] BUILD SUCCESSFUL in %.2fs\n", dur));
                        
                        result.setStatus(BuildResult.Status.SUCCESS);
                        result.setEndTime(System.currentTimeMillis());
                        finish(listener, result);
                    }

                    @Override
                    public void onFailed(String error) {
                        String endTs = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        emit(listener, "\n[ERROR] " + error + "\n");
                        emit(listener, "[" + endTs + "] BUILD FAILED\n");
                        
                        result.setStatus(BuildResult.Status.FAILED);
                        result.setEndTime(System.currentTimeMillis());
                        finish(listener, result);
                    }
                });

            } catch (Exception e) {
                result.setStatus(BuildResult.Status.FAILED);
                result.setEndTime(System.currentTimeMillis());
                emit(listener, "[ERROR] Unexpected build manager error: " + e.getMessage() + "\n");
                finish(listener, result);
            }
        });

        return result;
    }

    // 🟢 ပြင်ဆင်ချက်: Log များကို UI Thread ပေါ်တွင် ကြန့်ကြာမှုမရှိဘဲ တန်းပြပေးရန် သန့်စင်ထားပါသည်
    private void emit(BuildListener listener, String line) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onLogAppended(line);
            }
        });
    }

    private void finish(BuildListener listener, BuildResult result) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onBuildFinished(result);
            }
        });
    }
}
