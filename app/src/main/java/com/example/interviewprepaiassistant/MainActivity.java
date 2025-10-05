package com.example.interviewprepaiassistant;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String API_KEY = "AIzaSyDqAdcm9aCssaghAnllANADNiOFB9-wj5w";
    private static final String MODEL_NAME = "gemini-2.0-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1/models/" + MODEL_NAME + ":generateContent?key=" + API_KEY;

    private EditText editTextQuery;
    private Button buttonAskAI;
    private TextView textViewResponse;
    private ScrollView scrollViewResponse;
    private OkHttpClient client;

    private List<ChatMessage> chatHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextQuery = findViewById(R.id.editTextQuery);
        buttonAskAI = findViewById(R.id.buttonAskAI);
        textViewResponse = findViewById(R.id.textViewResponse);
        scrollViewResponse = findViewById(R.id.scrollViewResponse);

        client = new OkHttpClient();
        chatHistory = new ArrayList<>();

        buttonAskAI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = editTextQuery.getText().toString().trim();
                if (!query.isEmpty()) {
                    addUserMessage(query);
                    sendQueryToGemini(query);
                    editTextQuery.setText("");
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a question", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void addUserMessage(String message) {
        chatHistory.add(new ChatMessage("user", message));
        updateChatDisplay();
    }

    private void addAIMessage(String message) {
        chatHistory.add(new ChatMessage("ai", message));
        updateChatDisplay();
    }

    private void updateChatDisplay() {
        StringBuilder chatText = new StringBuilder();
        for (ChatMessage msg : chatHistory) {
            if (msg.role.equals("user")) {
                chatText.append("You: ").append(msg.message).append("\n\n");
            } else {
                chatText.append("AI: ").append(msg.message).append("\n\n");
            }
        }
        textViewResponse.setText(chatText.toString());

        scrollViewResponse.post(new Runnable() {
            @Override
            public void run() {
                scrollViewResponse.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void sendQueryToGemini(String query) {
        buttonAskAI.setEnabled(false);
        textViewResponse.append("AI is thinking...\n\n");

        try {
            JSONObject jsonBody = new JSONObject();
            JSONArray contentsArray = new JSONArray();

            for (ChatMessage msg : chatHistory) {
                JSONObject contentObj = new JSONObject();
                JSONArray partsArray = new JSONArray();
                JSONObject partObj = new JSONObject();

                partObj.put("text", msg.message);
                partsArray.put(partObj);
                contentObj.put("role", msg.role.equals("user") ? "user" : "model");
                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
            }

            jsonBody.put("contents", contentsArray);

            RequestBody body = RequestBody.create(
                    jsonBody.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            buttonAskAI.setEnabled(true);
                            if (!chatHistory.isEmpty()) {
                                chatHistory.remove(chatHistory.size() - 1);
                            }
                            addAIMessage("Error: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            buttonAskAI.setEnabled(true);

                            if (response.isSuccessful()) {
                                try {
                                    JSONObject jsonResponse = new JSONObject(responseBody);
                                    JSONArray candidates = jsonResponse.getJSONArray("candidates");
                                    JSONObject firstCandidate = candidates.getJSONObject(0);
                                    JSONObject content = firstCandidate.getJSONObject("content");
                                    JSONArray parts = content.getJSONArray("parts");
                                    String text = parts.getJSONObject(0).getString("text");

                                    if (!chatHistory.isEmpty() &&
                                            chatHistory.get(chatHistory.size() - 1).message.contains("thinking")) {
                                        chatHistory.remove(chatHistory.size() - 1);
                                    }
                                    addAIMessage(text);

                                } catch (Exception e) {
                                    addAIMessage("Error parsing response: " + e.getMessage());
                                }
                            } else {
                                addAIMessage("Error: " + response.code() + "\n" + responseBody);
                            }
                        }
                    });
                }
            });

        } catch (Exception e) {
            buttonAskAI.setEnabled(true);
            addAIMessage("Error creating request: " + e.getMessage());
        }
    }

    private static class ChatMessage {
        String role;
        String message;

        ChatMessage(String role, String message) {
            this.role = role;
            this.message = message;
        }
    }
}