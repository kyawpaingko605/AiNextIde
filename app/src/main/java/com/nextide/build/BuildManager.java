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

    // 🟢 MainActivity ကနေ triggerBuild(project, listener) ပုံစံဟောင်းအတိုင်း ခေါ်လို့ရအောင် Context မတောင်းတော့ဘဲ ပြန်ပြင်ထားပါတယ်
    public BuildResult triggerBuild(Project project, BuildListener listener) {
        BuildResult result = new BuildResult();
        result.setStatus(BuildResult.Status.RUNNING);
        
        // 🟢 Error ပြင်ဆင်ချက်: setStartTime နေရာမှာ သင့်ရဲ့ BuildResult ထဲက startTime field ကို တိုက်ရိုက်ထည့်သွင်းခြင်း
        long startTime = System.currentTimeMillis();

        executor.submit(() -> {
            try {
                String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                emit(listener, "╔══════════════════════════════════════╗\n");
                emit(listener, "║      Next IDE Build System v1.0      ║\n");
                emit(listener, "╚══════════════════════════════════════╝\n\n");
                emit(listener, "[" + ts + "] REAL COMPILATION STARTED\n");
                emit(listener, "[" + ts + "] Project: " + project.getName() + "\n");
                emit(listener, "[" + ts + "] Target: Android Application (.apk)\n\n");

                // 🟢 Context ကို project.getDirectory() ရှိရာနေရာ သို့မဟုတ် Application Context ကနေ အော်တိုယူခြင်း
                // (မှတ်ချက် - RealAndroidBuilder ကို project တည်နေရာသိရုံဖြင့် Dynamic သုံးနိုင်အောင် ပြင်ဆင်ရန်)
                // ဤနေရာတွင် Real ဝင်လုပ်မည့် Builder ကို ခေါ်ပါမည်
                File projectDir = project.getDirectory();
                
                // စမ်းသပ်မှုနှင့် တကယ့် Log ထုတ်ပေးမည့် အပိုင်း
                RealAndroidBuilder builder = new RealAndroidBuilder(projectDir.toURI().toURL().getContent() instanceof Context ? (Context)projectDir : null); 
                
                // သင့်ရဲ့ ရှိပြီးသား အောက်က စနစ်တွေကို Log Appended ဖြစ်အောင် လှမ်းချိတ်ပေးထားပါတယ်
                String lang = project.getLanguage().toLowerCase();
                
                // Real Builder ရဲ့ Log ကို UI Thread ပေါ် တန်းပြပေးခြင်း
                builder.buildProject(projectDir, new RealAndroidBuilder.BuildListener() {
                    @Override
                    public void onLog(String message) {
                        emit(listener, message);
                    }

                    @Override
                    public void onSuccess(File apkFile) {
                        String endTs = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        double dur = (System.currentTimeMillis() - startTime) / 1000.0;
                        
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
