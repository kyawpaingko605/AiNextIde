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

        // 🟢 UI တွင် ပြသမည့် နာမည်များ
        String[] displayModels = {
            "Google Gemini 2.0 Flash (Recommended)", 
            "Google Gemini 1.5 Flash", 
            "Google Gemini 1.5 Pro"
        };

        // 🟢 AiManager နှင့် API တွင် အသုံးပြုမည့် Model Code များ
        String[] actualModels = {
            "gemini-2.0-flash", 
            "gemini-1.5-flash", 
            "gemini-1.5-pro"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, displayModels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Load saved settings
        SharedPreferences prefs = requireActivity().getSharedPreferences("ai_prefs", Context.MODE_PRIVATE);
        etKey.setText(prefs.getString("api_key", ""));
        int savedModelPos = prefs.getInt("model_position", 0);
        spinner.setSelection(savedModelPos);

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            int selectedPos = spinner.getSelectedItemPosition();
            
            prefs.edit()
                .putString("api_key", etKey.getText().toString().trim())
                .putInt("model_position", selectedPos)
                // 🟢 AiManager ကနေ လှမ်းဖတ်ရလွယ်ကူအောင် တကယ့် Model Code (ဥပမာ- gemini-2.0-flash) ကိုပါ သိမ်းဆည်းလိုက်ပါတယ်
                .putString("selected_model", actualModels[selectedPos])
                .apply();
            dismiss();
        });

        builder.setView(view);
        return builder.create();
    }
}
