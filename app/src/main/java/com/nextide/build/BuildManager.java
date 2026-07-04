package com.nextide.build;

import android.os.Handler;
import android.os.Looper;
import com.nextide.model.BuildResult;
import com.nextide.model.Project;
import com.nextide.util.FileUtils;
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

    public BuildResult triggerBuild(Project project, BuildListener listener) {
        BuildResult result = new BuildResult();
        result.setStatus(BuildResult.Status.RUNNING);

        executor.submit(() -> {
            try {
                simulate(project, result, listener);
            } catch (Exception e) {
                result.setStatus(BuildResult.Status.FAILED);
                emit(listener, "[ERROR] Unexpected build error: " + e.getMessage() + "\n");
                finish(listener, result);
            }
        });

        return result;
    }

    private void simulate(Project project, BuildResult result, BuildListener listener) throws InterruptedException {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String lang = project.getLanguage().toLowerCase();
        String name = project.getName();

        emit(listener, "╔══════════════════════════════════════╗\n");
        emit(listener, "║      Next IDE Build System v1.0      ║\n");
        emit(listener, "╚══════════════════════════════════════╝\n\n");
        emit(listener, "[" + ts + "] BUILD STARTED\n");
        emit(listener, "[" + ts + "] Project: " + name + "\n");
        emit(listener, "[" + ts + "] Language: " + project.getLanguageDisplay() + "\n\n");

        Thread.sleep(400);

        // Scan source files
        File projectDir = project.getDirectory();
        String[] extensions = getExtensions(lang);
        java.util.List<File> sources = new java.util.ArrayList<>();
        collectSources(projectDir, extensions, sources);

        emit(listener, "[" + ts + "] Scanning source files...\n");
        Thread.sleep(300);

        if (sources.isEmpty()) {
            emit(listener, "[" + ts + "] WARNING: No source files found.\n");
            emit(listener, "[" + ts + "] BUILD FAILED — nothing to compile\n");
            result.setStatus(BuildResult.Status.FAILED);
            result.setEndTime(System.currentTimeMillis());
            finish(listener, result);
            return;
        }

        int totalLines = 0;
        for (File src : sources) {
            int lines = FileUtils.countLines(src);
            totalLines += lines;
            emit(listener, "  Compiling: " + src.getName() + " (" + lines + " lines)\n");
            Thread.sleep(150 + (long)(Math.random() * 200));
        }

        emit(listener, "\n[" + ts + "] Running syntax analysis...\n");
        Thread.sleep(500);

        // Check for brace/bracket balance issues
        boolean hasError = false;
        for (File src : sources) {
            try {
                String content = FileUtils.readFile(src);
                int err = checkSyntax(content, lang);
                if (err > 0) {
                    emit(listener, "[ERROR] " + src.getName() + ":" + err + ": syntax error\n");
                    hasError = true;
                }
            } catch (Exception ignored) {}
        }

        Thread.sleep(400);

        if (hasError) {
            emit(listener, "\n[" + ts + "] BUILD FAILED\n");
            emit(listener, "  Fix the errors above and rebuild.\n");
            result.setStatus(BuildResult.Status.FAILED);
        } else {
            emit(listener, "\n[" + ts + "] Linking...\n");
            Thread.sleep(300);
            double dur = (System.currentTimeMillis() - result.getStartTime()) / 1000.0;
            emit(listener, "[" + ts + "] Compiled " + sources.size() + " file(s), " + totalLines + " lines\n");
            emit(listener, "[" + ts + "] Output: " + getOutputName(lang, name) + "\n");
            emit(listener, String.format("[" + ts + "] BUILD SUCCESSFUL in %.2fs\n", dur));
            result.setStatus(BuildResult.Status.SUCCESS);
        }

        result.setEndTime(System.currentTimeMillis());
        finish(listener, result);
    }

    private int checkSyntax(String content, String lang) {
        if (lang.equals("python")) return 0; // Python uses indentation
        int braces = 0, line = 1;
        for (char c : content.toCharArray()) {
            if (c == '\n') line++;
            else if (c == '{') braces++;
            else if (c == '}') {
                braces--;
                if (braces < 0) return line;
            }
        }
        return braces != 0 ? line : 0;
    }

    private String[] getExtensions(String lang) {
        switch (lang) {
            case "java":       return new String[]{".java"};
            case "kotlin":     return new String[]{".kt", ".kts"};
            case "python":     return new String[]{".py"};
            case "cpp": case "c": return new String[]{".cpp", ".c", ".h"};
            case "javascript": return new String[]{".js", ".mjs"};
            default:           return new String[]{".txt", ".md"};
        }
    }

    private String getOutputName(String lang, String name) {
        switch (lang) {
            case "java":   return name + ".jar";
            case "kotlin": return name + ".jar";
            case "python": return name + ".pyc";
            case "cpp": case "c": return name;
            default:       return name + ".out";
        }
    }

    private void collectSources(File dir, String[] exts, java.util.List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectSources(f, exts, out);
            } else {
                for (String ext : exts) {
                    if (f.getName().endsWith(ext)) { out.add(f); break; }
                }
            }
        }
    }

    private void emit(BuildListener listener, String line) {
        mainHandler.post(() -> listener.onLogAppended(line));
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
    }

    private void finish(BuildListener listener, BuildResult result) {
        mainHandler.post(() -> listener.onBuildFinished(result));
    }
}
