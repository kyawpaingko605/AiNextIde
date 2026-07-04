package com.nextide.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nextide.R;
import com.nextide.databinding.FragmentEditorBinding;
import com.nextide.model.FileNode;
import com.nextide.util.FileUtils;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import java.io.IOException;

public class EditorFragment extends Fragment {
    private static final String ARG_PATH = "path";
    private static final String ARG_LANG = "lang";
    private static final String ARG_NAME = "name";

    private FragmentEditorBinding binding;
    private String filePath;
    private String language;
    private boolean isModified = false;

    public static EditorFragment newInstance(FileNode node) {
        EditorFragment f = new EditorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, node.getPath());
        args.putString(ARG_LANG, node.getLanguage());
        args.putString(ARG_NAME, node.getName());
        f.setArguments(args);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() == null) return;
        filePath = getArguments().getString(ARG_PATH);
        language = getArguments().getString(ARG_LANG, "text");

        setupEditor();
        loadFile();
    }

    private void setupEditor() {
        if (binding == null) return;
        CodeEditor editor = binding.codeEditor;

        // Dark theme
        editor.setColorScheme(new SchemeDarcula());
        editor.setTextSize(14);
        editor.setTypefaceText(android.graphics.Typeface.MONOSPACE);
        editor.setLineNumberEnabled(true);

        // Language support
        if ("java".equals(language) || "kotlin".equals(language)) {
            editor.setEditorLanguage(new JavaLanguage());
        }

        // Track modifications
        editor.subscribeEvent(io.github.rosemoe.sora.event.ContentChangeEvent.class,
                (event, unsubscribe) -> {
                    isModified = true;
                    updateSaveButton();
                });

        binding.btnSave.setOnClickListener(v -> saveFile());
        binding.btnSave.setEnabled(false);
    }

    private void loadFile() {
        if (filePath == null || binding == null) return;
        try {
            String content = FileUtils.readFile(new java.io.File(filePath));
            binding.codeEditor.setText(content);
            isModified = false;
            updateSaveButton();
        } catch (IOException e) {
            showToast("Failed to open file: " + e.getMessage());
        }
    }

    public void saveFile() {
        if (filePath == null || binding == null) return;
        try {
            String content = binding.codeEditor.getText().toString();
            FileUtils.writeFile(new java.io.File(filePath), content);
            isModified = false;
            updateSaveButton();
            showToast("Saved");
        } catch (IOException e) {
            showToast("Save failed: " + e.getMessage());
        }
    }

    private void updateSaveButton() {
        if (binding != null) {
            binding.btnSave.setEnabled(isModified);
        }
    }

    // Safe Toast Method to prevent Fragment Context Crashes
    private void showToast(String message) {
        Context context = getContext();
        if (context != null && isAdded()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isModified() { return isModified; }
    public String getFilePath() { return filePath; }
    public String getFileName() {
        return getArguments() != null ? getArguments().getString(ARG_NAME, "?") : "?";
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
