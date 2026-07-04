package com.nextide.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.nextide.R;

public class NewProjectDialog extends DialogFragment {
    public interface OnProjectCreatedListener {
        void onProjectCreated(String name, String language);
    }

    private OnProjectCreatedListener listener;

    public void setOnProjectCreatedListener(OnProjectCreatedListener l) { this.listener = l; }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_project, null);
        EditText etName = view.findViewById(R.id.et_project_name);
        Spinner spinLang = view.findViewById(R.id.spinner_language);

        String[] languages = {"Java", "Kotlin", "Python", "C++", "JavaScript"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, languages);
        spinLang.setAdapter(adapter);

        return new AlertDialog.Builder(requireContext())
                .setTitle("New Project")
                .setView(view)
                .setPositiveButton("Create", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        etName.setError("Project name required");
                        return;
                    }
                    String lang = languages[spinLang.getSelectedItemPosition()].toLowerCase();
                    if (listener != null) listener.onProjectCreated(name, lang);
                })
                .setNegativeButton("Cancel", null)
                .create();
    }
}
