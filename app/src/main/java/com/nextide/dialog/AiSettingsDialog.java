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

        // 🟢 UI တွင် User မြင်တွေ့ရမည့် Groq AI မော်ဒယ်အမည်များ
        String[] displayModels = {
            "Llama 3 8B (Ultra Fast - Recommended)", 
            "Llama 3 70B (High Quality)", 
            "Mixtral 8x7b (Balanced)"
        };

        // 🟢 Groq API Endpoint သို့ ပေးပို့ရမည့် တကယ့် Model ID များ
        String[] actualModels = {
            "llama3-8b-8192", 
            "llama3-70b-8192", 
            "mixtral-8x7b-32768"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, displayModels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // 🟢 ပြင်ဆင်ချက်: AiManager နှင့် အံကိုက်ဖြစ်စေရန် SharedPreferences နာမည်အား "ai_settings" ဟု ပြောင်းလဲပါသည်
        SharedPreferences prefs = requireActivity().getSharedPreferences("ai_settings", Context.MODE_PRIVATE);
        etKey.setText(prefs.getString("api_key", ""));
        int savedModelPos = prefs.getInt("model_position", 0);
        spinner.setSelection(savedModelPos);

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            int selectedPos = spinner.getSelectedItemPosition();
            
            prefs.edit()
                .putString("api_key", etKey.getText().toString().trim())
                .putInt("model_position", selectedPos)
                // 🟢 ပြင်ဆင်ချက်: AiManager ထဲက လှမ်းဖတ်မည့် Key Name အား "model_name" ဟု တိကျစွာ ပြောင်းလဲသိမ်းဆည်းပါသည်
                .putString("model_name", actualModels[selectedPos])
                .apply();
            dismiss();
        });

        builder.setView(view);
        return builder.create();
    }
}
