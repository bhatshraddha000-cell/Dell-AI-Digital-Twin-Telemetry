package com.dell.twin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;

/**
 * Predicts future laptop health and risks using the Groq LLM. Takes a deque of
 * recent telemetry rows (e.g., last 3) and asks the model to project status
 * 30‑40 minutes ahead.
 */
public class prediction {

    final static String apiKey = System.getenv("GROQ_API_KEY"); // replace with actual api key

    /**
     * Sends the telemetry history to Groq and prints the prediction.
     *
     * @param t deque of recent TelemetryRow objects (assumed non‑empty)
     * @throws Exception on network or parsing errors
     */
    public static void predict(ArrayDeque<TelemetryRow> t) throws Exception {
        StringBuilder trend = new StringBuilder();
        for (TelemetryRow record : t) {
            trend.append(record).append("\n");
        }

        String prompt = "You are a laptop digital twin assistant.\n"
                + "These are last 5 telemetry records: " + trend + "\n"
                + "Analyze the trend and Predict:\n"
                + "1. Health status after 30 to 40 minutes.\n"
                + "2. Possible issues.\n"
                + "3. Risk Level (low, medium, high).\n"
                + "4. Root cause of risk\n"
                + "5. Recommendations.\n"
                + "Format:\nHealth Status:\nIssue:\nRisk:\nRoot Cause:\nRecommendation:";

        // Build JSON request
        String json = """
        {
          "model": "llama-3.3-70b-versatile",
          "messages": [
            {"role": "user", "content": "%s"}
          ]
        }
        """.formatted(prompt.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("GROQ_API_URL")) //replace with actual groq api url
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String result = response.body();

        // Extract the content field (parsing)
        int start = result.indexOf("\"content\":\"") + 11;
        int end = result.indexOf("\"}", start);
        String content = result.substring(start, end).replace("\\n", "\n");
        System.out.println(content);
    }
}
