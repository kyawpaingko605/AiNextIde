package com.nextide.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.nextide.R;

public class AiSettingsDialog extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_ai_settings, null);

        Spinner spinner = view.findViewById(R.id.spinner_ai_model);
        EditText etKey = view.findViewById(R.id.et_api_key);

        // Models စာရင်းထည့်ခြင်း
        String[] models = {"Google Gemini Pro", "OpenAI GPT-4o", "Anthropic Claude 3.5"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Load saved settings
        SharedPreferences prefs = requireActivity().getSharedPreferences("ai_prefs", Context.MODE_PRIVATE);
        etKey.setText(prefs.getString("api_key", ""));
        int savedModelPos = prefs.getInt("model_position", 0);
        spinner.setSelection(savedModelPos);

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            prefs.edit()
                .putString("api_key", etKey.getText().toString().trim())
                .putInt("model_position", spinner.getSelectedItemPosition())
                .putString("selected_model", spinner.getSelectedItem().toString())
                .apply();
            dismiss();
        });

        builder.setView(view);
        return builder.create();
    }
}
