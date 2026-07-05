package com.nextide.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.File;
import java.io.IOException;

public class AiManager {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AiFixListener {
        void onFixSuccess(String fixedCode);
        void onFixFailed(String reason);
    }

    public static void requestAutoFix(Context context, File targetFile, String errorLog, AiFixListener listener) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", ""); 
        
        // 🟢 ပြင်ဆင်ချက်- Groq တွင် ပိုမိုတည်ငြိမ်သော Model Name အား အသုံးပြုရန်
        String modelName = prefs.getString("model_name", "llama3-70b-8192").trim(); 

        if (apiKey.isEmpty()) {
            emitFailed(listener, "Groq API Key is missing. Please set it in Settings.");
            return;
        }

        String currentCode = "";
        try {
            currentCode = FileUtils.readFile(targetFile);
        } catch (IOException e) {
            currentCode = "// (Could not read file content)";
        }

        String prompt = "You are an expert Android developer. Fix the compile errors in this Java file.\n\n"
                + "CURRENT CODE:\n" + currentCode + "\n\n"
                + "BUILD ERROR LOG:\n" + errorLog + "\n\n"
                + "Respond ONLY with the complete, fixed Java source code inside a single standard markdown code block. Do not include any explanations.";

        Gson gson = new Gson();
        JsonObject root = new JsonObject();
        root.addProperty("model", modelName);
        
        JsonArray messages = new JsonArray();
        JsonObject messageObj = new JsonObject();
        messageObj.addProperty("role", "user");
        messageObj.addProperty("content", prompt);
        messages.add(messageObj);
        root.add("messages", messages);
        
        root.addProperty("temperature", 0.2); 

        String jsonPayload = root.toString();
        
        // 🟢 OkHttp 4.12.0 အတွက် 400 Error လုံးဝမကျစေမည့် အသန့်ရှင်းဆုံး RequestBody တည်ဆောက်ပုံ
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonPayload, mediaType);

        // 🟢 Header များကို အတိအကျ သတ်မှတ်ခြင်း
        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                emitFailed(listener, "Network Error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 🟢 ပြင်ဆင်ချက်: try-with-resources သုံးပြီး ResponseBody ကို အသုံးပြုခြင်း
                try {
                    if (!response.isSuccessful()) {
                        String errorMsg = "Groq API Error Code: " + response.code();
                        ResponseBody errorBody = response.body();
                        if (errorBody != null) {
                            try {
                                errorMsg += "\nDetails: " + errorBody.string();
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        emitFailed(listener, errorMsg);
                        return;
                    }

                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        emitFailed(listener, "Empty response from Groq API");
                        return;
                    }

                    // 🟢 ပြင်ဆင်ချက်: ResponseBody ကို တစ်ကြိမ်သာ ဖတ်ခြင်း
                    String resStr = responseBody.string();
                    
                    if (resStr == null || resStr.isEmpty()) {
                        emitFailed(listener, "Empty response body from Groq API");
                        return;
                    }
                    
                    JsonObject resJson = gson.fromJson(resStr, JsonObject.class);
                    if (resJson == null || !resJson.has("choices")) {
                        emitFailed(listener, "Invalid JSON response from Groq API");
                        return;
                    }
                    
                    String rawText = resJson.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    String fixedCode = extractCodeFromMarkdown(rawText);
                    if (fixedCode == null || fixedCode.isEmpty()) {
                        emitFailed(listener, "No valid code extracted from AI response");
                        return;
                    }
                    
                    emitSuccess(listener, fixedCode);
                } catch (Exception e) {
                    emitFailed(listener, "Parsing Error: " + e.getMessage());
                }
            }
        });
    }

    private static String extractCodeFromMarkdown(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        if (rawText.contains("```java")) {
            int start = rawText.indexOf("```java") + 7;
            int end = rawText.indexOf("```", start);
            if (end > start) return rawText.substring(start, end).trim();
        } else if (rawText.contains("```")) {
            int start = rawText.indexOf("```") + 3;
            int end = rawText.indexOf("```", start);
            if (end > start) return rawText.substring(start, end).trim();
        }
        return rawText.trim();
    }

    private static void emitSuccess(AiFixListener listener, String fixedCode) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFixSuccess(fixedCode);
        });
    }

    private static void emitFailed(AiFixListener listener, String reason) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFixFailed(reason);
        });
    }
}
