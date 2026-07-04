package com.nextide.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileNode {
    private File file;
    private List<FileNode> children;
    private boolean expanded;
    private int depth;

    public FileNode(File file, int depth) {
        this.file = file;
        this.depth = depth;
        this.children = new ArrayList<>();
        this.expanded = false;
    }

    public File getFile() { return file; }
    public String getName() { return file.getName(); }
    public boolean isDirectory() { return file.isDirectory(); }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public List<FileNode> getChildren() { return children; }
    public int getDepth() { return depth; }
    public String getPath() { return file.getAbsolutePath(); }

    public void loadChildren() {
        children.clear();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                // Sort: directories first, then files, both alphabetically
                java.util.Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File f : files) {
                    if (!f.getName().startsWith(".")) {
                        children.add(new FileNode(f, depth + 1));
                    }
                }
            }
        }
    }

    public String getLanguage() {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) return "text";
        switch (name.substring(dot + 1).toLowerCase()) {
            case "java": return "java";
            case "kt": return "kotlin";
            case "py": return "python";
            case "js": return "javascript";
            case "ts": return "typescript";
            case "c": case "cpp": case "h": return "cpp";
            case "xml": return "xml";
            case "json": return "json";
            case "md": return "markdown";
            case "gradle": return "groovy";
            case "sh": return "shell";
            default: return "text";
        }
    }

    public int getIconRes() {
        if (isDirectory()) return android.R.drawable.ic_menu_more;
        String lang = getLanguage();
        switch (lang) {
            case "java": case "kotlin": return android.R.drawable.ic_menu_edit;
            default: return android.R.drawable.ic_menu_agenda;
        }
    }
}
