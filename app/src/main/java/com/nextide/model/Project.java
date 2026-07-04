package com.nextide.model;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Project {
    private String name;
    private String language;
    private String path;
    private long createdAt;
    private long lastModified;

    public Project() {}

    public Project(String name, String language, String path) {
        this.name = name;
        this.language = language;
        this.path = path;
        this.createdAt = System.currentTimeMillis();
        this.lastModified = System.currentTimeMillis();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public File getDirectory() { return new File(path); }

    public int getFileCount() {
        File dir = getDirectory();
        if (!dir.exists()) return 0;
        return countFiles(dir);
    }

    private int countFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) count += countFiles(f);
                else count++;
            }
        }
        return count;
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        return sdf.format(new Date(lastModified));
    }

    public String getLanguageDisplay() {
        if (language == null) return "Unknown";
        switch (language.toLowerCase()) {
            case "java": return "Java";
            case "kotlin": return "Kotlin";
            case "python": return "Python";
            case "javascript": return "JavaScript";
            case "cpp": return "C++";
            case "c": return "C";
            default: return language;
        }
    }
}
