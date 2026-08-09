package com.dell.twin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends a telemetry row to Groq for issue diagnosis.
 * WARNING: API key is hardcoded – move to environment variables.
 */

public class issueDiagnose {

    // ⚠️ HARDCODED API KEY – DO NOT COMMIT TO VERSION CONTROL

    private static final String apiKey = System.getenv("API_KEY");
    String url = System.getenv("API_URL");

     /**
     * Diagnoses issues from a telemetry row and prints the LLM response.
     * @param t the telemetry row
     * @throws Exception on network or parsing errors
     */

    public static void issue(TelemetryRow t) throws Exception {
        String prompt = "Analyze this telemetry.\n" +
                "CPU: " + t.getCpuLoad() + "%\n" +
                "Temperature: " + t.getTemperature() + "°C\n" +
                "Battery: " + t.getBatteryLevel() + "%\n" +
                "If any issue found provide:\n" +
                "1. Issues found\n" +
                "2. Root cause\n" +
                "3. Recommendations\n" +
                "Else just say 'No issue found'.";

         // Build JSON request manually (simpler than using a library)

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
                .uri(URI.create("url"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String result = response.body();

        //parsing: extract content between "content":" and next "}

        int start = result.indexOf("\"content\":\"") + 11;
        int end = result.indexOf("\"}", start);
        String content = result.substring(start, end).replace("\\n", "\n");
        System.out.println(content);
    }
}