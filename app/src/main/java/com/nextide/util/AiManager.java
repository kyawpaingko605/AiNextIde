package com.nextide.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AiManager {

    public interface AiFixListener {
        void onFixSuccess(String fixedCode);
        void onFixFailed(String reason);
    }

    // 🟢 အသုံးပြုမည့် Gemini Model List များ (လိုအပ်သလို လဲလှယ်သုံးနိုင်ရန် တည်ဆောက်ထားပါသည်)
    public static final String MODEL_GEMINI_2_0_FLASH = "gemini-2.0-flash";
    public static final String MODEL_GEMINI_1_5_FLASH = "gemini-1.5-flash";
    public static final String MODEL_GEMINI_1_5_PRO   = "gemini-1.5-pro";

    // 🎯 ပုံသေသုံးမည့် Model ကို ဤနေရာတွင် သတ်မှတ်နိုင်သည် (ဥပမာ - Gemini 2.0 Flash)
    private static final String CURRENT_MODEL = MODEL_GEMINI_2_0_FLASH;

    public static void requestAutoFix(Context context, File targetFile, String errorLog, AiFixListener listener) {
        // Background Thread ဖြင့် Network Request လုပ်ခြင်း
        new Thread(() -> {
            try {
                // ၁။ သင့်ရဲ့ SharedPreferences သို့မဟုတ် Settings ထဲမှ API Key ကို ယူခြင်း
                // (လက်ရှိစမ်းသပ်ရန်အတွက် သင့် Settings Dialog က Key ကို ယူသုံးပါမည်၊ မရှိလျှင် စာသားအလွတ် ပြပါမည်)
                String apiKey = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
                        .getString("api_key", "");

                if (apiKey.isEmpty()) {
                    sendFailed(listener, "API Key is missing. Please set it in AI Settings.");
                    return;
                }

                // ၂။ တိုင်တည်မည့် URL လမ်းကြောင်းကို စနစ်တကျ တည်ဆောက်ခြင်း
                String urlString = "https://generativelanguage.googleapis.com/v1beta/models/" 
                        + CURRENT_MODEL + ":generateContent?key=" + apiKey;

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                // ၃။ ဖိုင်ရဲ့ လက်ရှိ ကုဒ်တွေကို ဖတ်ယူခြင်း
                String currentCode = "";
                if (targetFile.exists()) {
                    currentCode = FileUtils.readFile(targetFile);
                }

                // ၄။ AI ဆီ ပို့မည့် Prompt မက်ဆေ့ခ်ျ တည်ဆောက်ခြင်း
                String prompt = "You are an expert Android compiler assistant.\n"
                        + "The following Java file has a build error.\n\n"
                        + "--- CURRENT CODE ---\n" + currentCode + "\n\n"
                        + "--- ERROR LOG ---\n" + errorLog + "\n\n"
                        + "Fix the syntax or logic error and return ONLY the fully corrected Java code. "
                        + "Do NOT wrap the response in markdown blocks like ```java or ```. No explanations.";

                // ၅။ Google Gemini Standard JSON Payload ဖွဲ့စည်းပုံအတိုင်း တည်ဆောက်ခြင်း
                JSONObject jsonBody = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject contentObj = new JSONObject();
                JSONArray partsArray = new JSONArray();
                JSONObject partObj = new JSONObject();

                partObj.put("text", prompt);
                partsArray.put(partObj);
                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
                jsonBody.put("contents", contentsArray);

                // ၆။ Request Data အား ပို့လွှတ်ခြင်း
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // ၇။ Response အား ဖတ်ယူပြီး စစ်ဆေးခြင်း
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }

                    // ၈။ Gemini Response JSON ထဲမှ စာသားကို ဆွဲထုတ်ခြင်း
                    JSONObject resJson = new JSONObject(response.toString());
                    String fixedCode = resJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    // Markdown tag များ ပါလာပါက ဖယ်ရှားခြင်း
                    if (fixedCode.contains("```java")) {
                        fixedCode = fixedCode.substring(fixedCode.indexOf("```java") + 7);
                        fixedCode = fixedCode.substring(0, fixedCode.lastIndexOf("```"));
                    } else if (fixedCode.contains("```")) {
                        fixedCode = fixedCode.substring(fixedCode.indexOf("```") + 3);
                        fixedCode = fixedCode.substring(0, fixedCode.lastIndexOf("```"));
                    }

                    sendSuccess(listener, fixedCode.trim());
                } else {
                    // HTTP Error တက်ပါက Response Message ကို ဖတ်ပြီး အကြောင်းအရင်း ရှာဖွေခြင်း
                    sendFailed(listener, "HTTP Error Code: " + responseCode + " (" + conn.getResponseMessage() + ")");
                }
                conn.disconnect();

            } catch (Exception e) {
                sendFailed(listener, "Connection Error: " + e.getMessage());
            }
        }).start();
    }

    private static void sendSuccess(AiFixListener listener, String code) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) listener.onFixSuccess(code);
        });
    }

    private static void sendFailed(AiFixListener listener, String reason) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) listener.onFixFailed(reason);
        });
    }
}
