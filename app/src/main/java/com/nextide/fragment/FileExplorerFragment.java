package com.nextide.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.nextide.R;
import com.nextide.adapter.FileTreeAdapter;
import com.nextide.databinding.FragmentFileExplorerBinding;
import com.nextide.dialog.NewFileDialog;
import com.nextide.model.FileNode;
import com.nextide.model.Project;
import java.io.File;

public class FileExplorerFragment extends Fragment {
    public interface OnFileSelectedListener {
        void onFileSelected(FileNode node);
    }

    private FragmentFileExplorerBinding binding;
    private FileTreeAdapter adapter;
    private Project currentProject;
    private OnFileSelectedListener fileSelectedListener;
    private FileNode selectedDir;

    public static FileExplorerFragment newInstance() {
        return new FileExplorerFragment();
    }

    public void setOnFileSelectedListener(OnFileSelectedListener l) {
        this.fileSelectedListener = l;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFileExplorerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context context = getContext();
        if (context == null || binding == null) return;

        binding.recyclerFiles.setLayoutManager(new LinearLayoutManager(context));
        binding.fabNewFile.setOnClickListener(v -> showNewFileDialog());
        
        // View တက်လာချိန်တွင် Project ရှိနေပြီးသားဆိုပါက ဒေတာချက်ချင်းထုတ်ပြမည်
        if (currentProject != null) {
            displayProjectStructure();
        } else {
            binding.tvNoProject.setVisibility(View.VISIBLE);
        }
    }

    public void loadProject(Project project) {
        this.currentProject = project;
        this.selectedDir = null;
        
        // အကယ်၍ Fragment View က အဆင်သင့်ဖြစ်နေပြီဆိုမှ တန်းဆွဲမည်
        // မဖြစ်သေးပါက onViewCreated ထဲရောက်မှ အလိုအလျောက် ဆွဲသွားပါလိမ့်မည် (No More Crash)
        if (binding != null && isAdded()) {
            displayProjectStructure();
        }
    }

    private void displayProjectStructure() {
        if (binding == null || currentProject == null) return;

        File dir = currentProject.getDirectory();
        FileNode root = new FileNode(dir, 0);
        adapter = new FileTreeAdapter(root);
        adapter.setOnFileClickListener(new FileTreeAdapter.OnFileClickListener() {
            @Override
            public void onFileClick(FileNode node) {
                if (!node.isDirectory() && fileSelectedListener != null) {
                    fileSelectedListener.onFileSelected(node);
                }
                selectedDir = node.isDirectory() ? node : null;
            }

            @Override
            public boolean onFileLongClick(FileNode node) {
                showFileContextMenu(node);
                return true;
            }
        });
        binding.recyclerFiles.setAdapter(adapter);
        binding.tvNoProject.setVisibility(View.GONE);
    }

    private void showNewFileDialog() {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        if (currentProject == null) {
            Toast.makeText(context, "Open a project first", Toast.LENGTH_SHORT).show();
            return;
        }
        NewFileDialog dialog = new NewFileDialog();
        dialog.setOnFileCreatedListener((name, isDir) -> {
            File parentDir = (selectedDir != null && selectedDir.isDirectory())
                    ? selectedDir.getFile()
                    : currentProject.getDirectory();
            File newFile = new File(parentDir, name);
            try {
                if (isDir) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                }
                if (adapter != null) adapter.refresh();
            } catch (Exception e) {
                showToast("Failed: " + e.getMessage());
            }
        });
        dialog.show(getChildFragmentManager(), "new_file");
    }

    private void showFileContextMenu(FileNode node) {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        String[] options = {"Delete", "Rename"};
        new AlertDialog.Builder(context)
                .setTitle(node.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) confirmDelete(node);
                    else showRenameDialog(node);
                })
                .show();
    }

    private void confirmDelete(FileNode node) {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        new AlertDialog.Builder(context)
                .setTitle("Delete " + node.getName() + "?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    com.nextide.util.FileUtils.deleteRecursive(node.getFile());
                    if (adapter != null) adapter.refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(FileNode node) {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        android.widget.EditText et = new android.widget.EditText(context);
        et.setText(node.getName());
        new AlertDialog.Builder(context)
                .setTitle("Rename")
                .setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        File newFile = new File(node.getFile().getParent(), newName);
                        node.getFile().renameTo(newFile);
                        if (adapter != null) adapter.refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void refresh() {
        if (adapter != null && binding != null && isAdded()) {
            adapter.refresh();
        }
    }

    private void showToast(String message) {
        Context context = getContext();
        if (context != null && isAdded()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
