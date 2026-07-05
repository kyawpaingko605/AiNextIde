package com.nextide.build;

import android.content.Context;
import com.nextide.model.BuildResult;
import com.nextide.model.Project;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BuildManager - Android APK Build ကို စီမံခန့်ခွဲခြင်း
 * 🟢 RealAndroidBuilder ကို အသုံးပြု၍ On-Device Build ပြုလုပ်တယ်
 */
public class BuildManager {

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public interface BuildListener {
        void onLogAppended(String line);
        void onBuildFinished(BuildResult result);
    }

    /**
     * Build process စတင်ခြင်း
     * @param context Android Context
     * @param project Build ပြုလုပ်မည့် Project
     * @param listener Build progress listener
     * @return BuildResult
     */
    public BuildResult triggerBuild(Context context, Project project, BuildListener listener) {
        BuildResult result = new BuildResult();
        
        executorService.submit(() -> {
            try {
                if (listener != null) {
                    listener.onLogAppended("🔨 Build started for project: " + project.getName());
                }

                // 🟢 RealAndroidBuilder ကို အသုံးပြုပြီး APK build ပြုလုပ်ခြင်း
                RealAndroidBuilder builder = new RealAndroidBuilder();
                builder.build(context, project, new RealAndroidBuilder.BuildCallback() {
                    @Override
                    public void onProgress(String message) {
                        if (listener != null) {
                            listener.onLogAppended(message);
                        }
                    }

                    @Override
                    public void onComplete(BuildResult buildResult) {
                        if (listener != null) {
                            listener.onBuildFinished(buildResult);
                        }
                    }
                });
            } catch (Exception e) {
                if (listener != null) {
                    listener.onLogAppended("❌ Build Error: " + e.getMessage());
                }
                result.setStatus(BuildResult.Status.FAILED);
                result.setErrorMessage(e.getMessage());
                if (listener != null) {
                    listener.onBuildFinished(result);
                }
            }
        });

        return result;
    }

    /**
     * Build process ကို ရပ်တန့်ခြင်း
     */
    public void stopBuild() {
        executorService.shutdownNow();
        executorService = Executors.newSingleThreadExecutor();
    }
}
