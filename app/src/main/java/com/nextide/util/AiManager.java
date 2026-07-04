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
        
        String modelName = prefs.getString("model_name", "llama-3.3-70b-versatile").trim(); 

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
        
        // 🟢 OkHttp 4.12.0 နှင့် အကိုက်ညီဆုံးဖြစ်အောင် MediaType သတ်မှတ်ခြင်း
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        
        // 🟢 400 Error ကင်းဝေးစေရန် OkHttp 4.x ရေးထုံးအမှန် (Content, MediaType) အတိုင်း တည်ဆောက်ခြင်း
        RequestBody body = RequestBody.create(jsonPayload, mediaType);

        // 🟢 Header ကွန်ဖလစ်မဖြစ်စေရန်နှင့် စိတ်ချရစေရန် .header() ဖြင့်သာ တိုက်ရိုက်ထည့်သွင်းခြင်း
        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json; charset=utf-8")
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
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        String errorBodyStr = responseBody != null ? responseBody.string() : "No error body";
                        emitFailed(listener, "Groq API Error Code: " + response.code() + "\nDetails: " + errorBodyStr);
                        return;
                    }

                    String resStr = responseBody.string();
                    JsonObject resJson = gson.fromJson(resStr, JsonObject.class);
                    
                    String rawText = resJson.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    String fixedCode = extractCodeFromMarkdown(rawText);
                    emitSuccess(listener, fixedCode);
                } catch (Exception e) {
                    emitFailed(listener, "Parsing Error: " + e.getMessage());
                }
            }
        });
    }

    private static String extractCodeFromMarkdown(String rawText) {
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
