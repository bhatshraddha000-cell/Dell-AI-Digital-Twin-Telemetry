package com.dell.twin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class chatBox {

    final static String apiKey = System.getenv("GROQ_API_KEY"); // replace with actual api key

    public static void chat(TelemetryRow t) throws Exception {
        System.out.println("Welcome to AI Assistant");
        Scanner sc = new Scanner(System.in);
        String str = "";
        do {
            System.out.print("Ask: ");
            str = sc.nextLine();
            String prompt = "You are a laptop digital twin assistant.\n"
                    + "CPU: " + t.getCpuLoad() + "%\n"
                    + "Temperature: " + t.getTemperature() + "°C\n"
                    + "Battery: " + t.getBatteryLevel() + "%\n"
                    + "Answer the user's question using telemetry only if relevant.\n"
                    + "If the question is general (hi, hello, good morning, thank you, etc.), respond normally.\n"
                    + "Question: " + str;

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
                    .uri(URI.create("GROQ_API_URL")) //replace with groq api url
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = response.body();

            int start = result.indexOf("\"content\":\"") + 11;
            int end = result.indexOf("\"}", start);
            String content = result.substring(start, end).replace("\\n", "\n");
            System.out.println(content);
        } while (!str.contains("bye"));
    }
}
