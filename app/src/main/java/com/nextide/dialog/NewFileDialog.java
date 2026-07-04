package com.nextide.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.nextide.R;

public class NewFileDialog extends DialogFragment {
    public interface OnFileCreatedListener {
        void onFileCreated(String name, boolean isDirectory);
    }

    private OnFileCreatedListener listener;

    public void setOnFileCreatedListener(OnFileCreatedListener l) { this.listener = l; }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_file, null);
        EditText etName = view.findViewById(R.id.et_file_name);
        CheckBox cbDir = view.findViewById(R.id.cb_is_directory);

        return new AlertDialog.Builder(requireContext())
                .setTitle("New File / Folder")
                .setView(view)
                .setPositiveButton("Create", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        etName.setError("Name required");
                        return;
                    }
                    if (listener != null) listener.onFileCreated(name, cbDir.isChecked());
                })
                .setNegativeButton("Cancel", null)
                .create();
    }
}
