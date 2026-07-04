package com.nextide.util;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;

public class AiManager {
    private static final OkHttpClient client = new OkHttpClient();

    public interface AiFixListener {
        void onFixSuccess(String fixedCode);
        void onFixFailed(String reason);
    }

    public static void requestAutoFix(Context ctx, File sourceFile, String errorMessage, AiFixListener listener) {
        SharedPreferences prefs = ctx.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "");
        String model = prefs.getString("selected_model", "Google Gemini Pro");

        if (apiKey.isEmpty()) {
            listener.onFixFailed("AI API Key is missing. Please set it up in AI Settings.");
            return;
        }

        try {
            // ပြင်ဆင်ရမည့် Original Code ကို အရင်ဖတ်ယူခြင်း
            String originalCode = FileUtils.readFile(sourceFile);

            // AI ဆီ ပို့မည့် စနစ်တကျ ညွှန်ကြားချက် (Prompt)
            String prompt = "You are an Android expert compiler fixer. Fix this code based on the compilation error.\n" +
                    "Return ONLY the raw fixed source code inside your response. Do not include markdowns like ```java.\n\n" +
                    "[COMPILATION ERROR]:\n" + errorMessage + "\n\n" +
                    "[ORIGINAL CODE]:\n" + originalCode;

            if (model.contains("Gemini")) {
                callGeminiAPI(apiKey, prompt, listener);
            } else if (model.contains("GPT")) {
                callOpenAiAPI(apiKey, prompt, listener);
            } else {
                listener.onFixFailed("Selected model processing is not active yet.");
            }

        } catch (Exception e) {
            listener.onFixFailed("Failed to read source file: " + e.getMessage());
        }
    }

    private static void callGeminiAPI(String key, String prompt, AiFixListener listener) {
        String url = "[https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=](https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=)" + key;

        try {
            JSONObject json = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));

            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { listener.onFixFailed(e.getMessage()); }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String resStr = response.body().string();
                        JSONObject resObj = new JSONObject(resStr);
                        String fixedCode = resObj.getJSONArray("candidates")
                                .getJSONObject(0).getJSONObject("content")
                                .getJSONArray("parts").getJSONObject(0).getString("text");
                        listener.onFixSuccess(fixedCode.trim());
                    } catch (Exception e) { listener.onFixFailed("AI Response parsing error."); }
                }
            });
        } catch (Exception e) { listener.onFixFailed(e.getMessage()); }
    }

    private static void callOpenAiAPI(String key, String prompt, AiFixListener listener) {
        String url = "[https://api.openai.com/v1/chat/completions](https://api.openai.com/v1/chat/completions)";
        // OpenAI အတွက် လိုအပ်သော JSON Formatter ကို ဤနေရာတွင် ထပ်မံထည့်သွင်းနိုင်သည်
    }
}
