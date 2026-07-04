package com.nextide.util;

import android.content.Context;
import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.File;
import java.io.IOException;

public class AiManager {

    // 🟢 Groq API Endpoint ဖြစ်ပါတယ်
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public interface AiFixListener {
        void onFixSuccess(String fixedCode);
        void onFixFailed(String reason);
    }

    public static void requestAutoFix(Context context, File targetFile, String errorLog, AiFixListener listener) {
        // ၁။ SharedPreferences ထဲကနေ User ထည့်ထားတဲ့ Groq API Key ကို လှမ်းယူပါမယ်
        android.content.SharedPreferences prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", ""); 
        
        // မြန်ဆန်ပြီး ကုဒ်ရေးသားမှု တိကျတဲ့ llama3-8b-8192 သို့မဟုတ် llama3-70b-8192 မော်ဒယ်ကို သုံးနိုင်ပါတယ်
        String modelName = prefs.getString("model_name", "llama3-8b-8192"); 

        if (apiKey.isEmpty()) {
            listener.onFixFailed("Groq API Key is missing. Please set it in Settings.");
            return;
        }

        // ၂။ ဖိုင်ထဲက လက်ရှိ Error တက်နေတဲ့ ကုဒ်အဟောင်းကို ဖတ်ယူခြင်း
        String currentCode = "";
        try {
            currentCode = FileUtils.readFile(targetFile);
        } catch (IOException e) {
            currentCode = "// (Could not read file content)";
        }

        // ၃။ Groq AI နားလည်အောင် Prompt ပုံစံ စနစ်တကျ ပြင်ဆင်ခြင်း (Escaping ပြဿနာမရှိစေရန် သန့်စင်ထားပါသည်)
        String prompt = "You are an expert Android developer. Fix the compile errors in this Java file.\n\n"
                + "CURRENT CODE:\n" + currentCode + "\n\n"
                + "BUILD ERROR LOG:\n" + errorLog + "\n\n"
                + "Respond ONLY with the complete, fixed Java source code inside a single standard markdown code block. Do not include any explanations.";

        // ၄။ Gson Object စနစ်ဖြင့် စနစ်တကျ JSON ပြောင်းခြင်း (Error 400 လုံးဝမတက်အောင် ကာကွယ်ပေးပါတယ်)
        Gson gson = new Gson();
        JsonObject root = new JsonObject();
        root.addProperty("model", modelName);
        
        JsonArray messages = new JsonArray();
        JsonObject messageObj = new JsonObject();
        messageObj.addProperty("role", "user");
        messageObj.addProperty("content", prompt);
        messages.add(messageObj);
        root.add("messages", messages);
        
        root.addProperty("temperature", 0.2); // ကုဒ်တိကျမှုရှိစေရန် temperature ကို လျှော့ထားပါတယ်

        // 🟢 ပြင်ဆင်ချက်: OkHttp 4.x နှင့် ကိုက်ညီသော RequestBody တည်ဆောက်ပုံသို့ ပြောင်းလဲခြင်း
        RequestBody body = RequestBody.create(
                root.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        // ၅။ Header တွင် Groq Bearer Token ထည့်သွင်းခြင်း
        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
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
                        listener.onFixFailed("Groq API Error Code: " + response.code() + " - " + response.message());
                        return;
                    }

                    String resStr = responseBody.string();
                    JsonObject resJson = gson.fromJson(resStr, JsonObject.class);
                    
                    // Groq Response ထဲက စာသားကို ဆွဲထုတ်ခြင်း
                    String rawText = resJson.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    // Code block (```java ... ```) ပါလာပါက ကုဒ်သီးသန့် သန့်စင်ထုတ်ယူခြင်း
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
