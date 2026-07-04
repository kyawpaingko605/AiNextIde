package com.nextide.util;

import android.content.Context;
import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.File;
import java.io.IOException;

public class AiManager {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public interface AiFixListener {
        void onFixSuccess(String fixedCode);
        void onFixFailed(String reason);
    }

    public static void requestAutoFix(Context context, File targetFile, String errorLog, AiFixListener listener) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", ""); 
        
        // 🟢 လက်ရှိအလုပ်လုပ်နေသော Model အမှန်ဖြစ်ကြောင်း သေချာစေရန် .trim() သုံးထားပါသည်
        String modelName = prefs.getString("model_name", "llama-3.1-8b-instant").trim(); 

        if (apiKey.isEmpty()) {
            listener.onFixFailed("Groq API Key is missing. Please set it in Settings.");
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
        
        // 🟢 OkHttp Version အားလုံးနှင့် အဆင်ပြေစေရန် MediaType သတ်မှတ်ပုံကို ပြောင်းလဲထားပါသည်
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(mediaType, jsonPayload);

        // 🟢 ပြင်ဆင်ချက်: Error 400 လုံးဝမကျစေရန် Content-Type Header အား တိုက်ရိုက်ပြန်လည်ထည့်သွင်းပေးထားပါသည်
        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey.trim())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                listener.onFixFailed("Network Error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        String errorBodyStr = responseBody != null ? responseBody.string() : "No error body";
                        listener.onFixFailed("Groq API Error Code: " + response.code() + "\nDetails: " + errorBodyStr);
                        return;
                    }

                    String resStr = responseBody.string();
                    JsonObject resJson = gson.fromJson(resStr, JsonObject.class);
                    
                    String rawText = resJson.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    String fixedCode = extractCodeFromMarkdown(rawText);
                    listener.onFixSuccess(fixedCode);
                } catch (Exception e) {
                    listener.onFixFailed("Parsing Error: " + e.getMessage());
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
}
