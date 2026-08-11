package com.dell.twin;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for interacting with Groq's Llama 3.3 70B model. Uses OkHttp for HTTP
 * requests and Jackson for JSON handling. API key is loaded from .env file;
 * falls back to a dummy key for testing.
 */
public class GroqClient {

    private static final String GROQ_API_KEY
            = System.getenv("GROQ_API_KEY");
    private static final String MODEL = "llama-3.3-70b-versatile";

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GroqClient() {
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isEmpty()) {
            key = "dummy_key_for_testing"; // fallback to avoid null
        }
        this.apiKey = key;
    }

    /**
     * Returns true if using the dummy key (useful for offline testing).
     */
    public boolean isDummyKey() {
        return this.apiKey.equals("dummy_key_for_testing");
    }

    /**
     * Sends a chat completion request to Groq.
     *
     * @param systemInstructions system-level prompt (e.g., role, constraints)
     * @param userPrompt user query
     * @return raw JSON response from the API
     * @throws IOException on network errors
     */
    public String askGroq(String systemInstructions, String userPrompt) throws IOException {
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemInstructions);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        List<Map<String, String>> messages = List.of(systemMessage, userMessage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1); // low temperature for deterministic output
        requestBody.put("max_tokens", 400);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // Return a structured error message so the caller can handle it gracefully
                return "{\"diagnosis\":\"API Connection Timeout\",\"recommendation\":\"Verify local network gateway infrastructure.\",\"confidence\":0.0}";
            }
            return response.body().string();
        }
    }

    /**
     * Extracts the 'content' field from the LLM's JSON response.
     *
     * @param jsonResponse raw JSON from Groq
     * @return the assistant's textual answer
     * @throws IOException if parsing fails
     */
    public String parseGroqResponse(String jsonResponse) throws IOException {
        Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            return (String) message.get("content");
        }
        // Fallback error message
        return "{\"diagnosis\":\"Parsing anomaly detected\",\"recommendation\":\"Retry token sequence execution.\",\"confidence\":0.0}";
    }
}
