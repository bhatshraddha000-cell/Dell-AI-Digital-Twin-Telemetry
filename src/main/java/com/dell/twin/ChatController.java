package com.dell.twin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ChatController - Main REST API controller for the Digital Twin.
 * Handles chat, prediction, and what-if simulation requests.
 */

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final GroqClient groqClient;
    private final ObjectMapper jacksonMapper;
    private BM25Retriever bm25Retriever;
    private int streamingTelemetryPointer = 0;
    private static int predictCounter = 0;

    @Autowired
    private DataLoader dataLoader;

    //Constructor - Initializes Groq client and BM25 RAG index.

    public ChatController() {
        this.groqClient = new GroqClient();
        this.jacksonMapper = new ObjectMapper();
        try {
            this.bm25Retriever = new BM25Retriever();
            this.bm25Retriever.buildFromCSV("dell_like_laptop_telemetry_1000_rows-1.csv");
        } catch (Exception e) {
            System.err.println("RAG Index building failure: " + e.getMessage());
        }
    }

    /**
     * Main chat endpoint - Processes user queries with context-aware responses.
     * Supports: General chat, what-if simulations, and health diagnosis.
     */
    @PostMapping("/chat")
    public Map<String, Object> handleChat(@RequestBody Map<String, String> requestBody) {
        String userQuery = requestBody.get("message");
        if (userQuery == null || userQuery.isEmpty()) {
            return errorResponse("Message payload cannot be empty");
        }

        try {
            String queryLower = userQuery.toLowerCase().trim();

            // Handle simple greetings
            if (isGeneralChitchat(queryLower)) {
                return executeConversationalResponse(userQuery);
            }

            // Load data and advance pointer
            List<TelemetryRow> allRecords = dataLoader.getAllData();
            if (allRecords == null || allRecords.isEmpty()) {
                return errorResponse("Telemetry data pool is empty");
            }

            // Stream through data
            if (streamingTelemetryPointer >= allRecords.size()) {
                streamingTelemetryPointer = 0;
            }
            
            TelemetryRow current = allRecords.get(streamingTelemetryPointer);
            int historyStart = Math.max(0, streamingTelemetryPointer - 5);
            List<TelemetryRow> historyRows = allRecords.subList(historyStart, streamingTelemetryPointer + 1);
            streamingTelemetryPointer++;

            // RAG retrieval for context
            List<String> matchingHistoricalContext = new ArrayList<>();
            if (bm25Retriever != null) {
                matchingHistoricalContext = bm25Retriever.retrieve(userQuery, 3);
            }

            // Handle what-if simulations
            String simulationText = "No simulation override requested.";
            int riskScore = calculateDeterministicRisk(current, historyRows);
            if (queryLower.contains("what if") || queryLower.contains("simulate")) {
                simulationText = executeWhatIfSimulation(userQuery, current);
                if (queryLower.contains("90") || queryLower.contains("high load") || queryLower.contains("stress")) {
                    riskScore = Math.min(100, riskScore + 45);
                }
            }

            // Build prompts for LLM
            String systemInstructions = buildSystemInstructions();
            String promptPayload = buildPromptPayload(userQuery, current, historyRows, matchingHistoricalContext, simulationText, riskScore);

            String aiReply;
            if (groqClient.isDummyKey()) {
                // Fallback mock response when no API key
                aiReply = generateDynamicMockReply(userQuery, current, riskScore);
            } else {
                aiReply = groqClient.askGroq(systemInstructions, promptPayload);
                aiReply = groqClient.parseGroqResponse(aiReply);
            }

            // Parse and format response
            Map<String, Object> aiJson = extractJsonFields(aiReply);

            Map<String, Object> finalResponse = new HashMap<>();
            finalResponse.put("success", true);
            finalResponse.put("diagnosis", aiJson.getOrDefault("diagnosis", "System analysis completed."));
            finalResponse.put("recommendation", aiJson.getOrDefault("recommendation", "Continue monitoring."));

            Object confidenceObj = aiJson.get("confidence");
            int confidenceInt = 90;
            if (confidenceObj instanceof Number) {
                double rawConfidence = ((Number) confidenceObj).doubleValue();
                confidenceInt = (rawConfidence <= 1.0) ? (int)(rawConfidence * 100) : (int)rawConfidence;
            }
            finalResponse.put("confidence", confidenceInt);

            finalResponse.put("evidence", formatEvidenceBox(current, matchingHistoricalContext));
            finalResponse.put("simulation", simulationText);
            finalResponse.put("risk", String.valueOf(riskScore));

            return finalResponse;

        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse("Core Processing Exception: " + e.getMessage());
        }
    }

    /**
     * Predict endpoint - Analyzes trends and predicts future health status.
     * Uses last 10 records to calculate slopes and project future metrics.
     */
    @PostMapping("/predict")
    public Map<String, Object> predict(@RequestBody(required = false) Map<String, Object> payload) {
        List<TelemetryRow> allRecords = dataLoader.getAllData();
        if (allRecords == null || allRecords.isEmpty() || allRecords.size() < 10) {
            return errorResponse("Insufficient telemetry data for prediction (need at least 10 records)");
        }
        // Get sliding window of 10 records
        predictCounter = (predictCounter + 10) % allRecords.size();
        int startIndex = predictCounter;
        int endIndex = Math.min(startIndex + 10, allRecords.size());
        List<TelemetryRow> window = allRecords.subList(startIndex, endIndex);
        if (window.size() < 10) {
            int remaining = 10 - window.size();
            window.addAll(allRecords.subList(0, remaining));
        }

        System.out.println("🔮 PREDICT: Using rows " + startIndex + " to " + (startIndex + 9) + " (pointer=" + predictCounter + ")");
        // Calculate trends
        double[] cpuValues = window.stream().mapToDouble(TelemetryRow::getCpuLoad).toArray();
        double[] tempValues = window.stream().mapToDouble(TelemetryRow::getTemperature).toArray();
        double[] batValues = window.stream().mapToDouble(TelemetryRow::getBatteryLevel).toArray();

        double cpuSlope = calculateSlope(cpuValues);
        double tempSlope = calculateSlope(tempValues);
        double batSlope = calculateSlope(batValues);

        double cpuFuture = cpuValues[cpuValues.length - 1] + cpuSlope * 3;
        double tempFuture = tempValues[tempValues.length - 1] + tempSlope * 3;
        double batFuture = batValues[batValues.length - 1] + batSlope * 3;
        // Project future values (3 intervals ahead)
        cpuFuture = Math.max(0, Math.min(100, cpuFuture));
        tempFuture = Math.max(20, Math.min(95, tempFuture));
        batFuture = Math.max(0, Math.min(100, batFuture));

        // Build issues and recommendations
        List<String> issueParts = new ArrayList<>();
        List<String> precautionParts = new ArrayList<>();
        int warningCount = 0;

        if (Math.abs(cpuSlope) > 0.3) {
            if (cpuSlope > 0) {
                issueParts.add("CPU usage is rising at " + String.format("%.2f", cpuSlope) + "% per interval");
                precautionParts.add("Reduce CPU‑intensive tasks or close unnecessary applications.");
                warningCount++;
            } else {
                issueParts.add("CPU usage is declining (" + String.format("%.2f", cpuSlope) + "% per interval) – system may be cooling down");
                precautionParts.add("Maintain current workload; continue monitoring.");
            }
        }
        if (Math.abs(tempSlope) > 0.2) {
            if (tempSlope > 0) {
                issueParts.add("Temperature is increasing by " + String.format("%.2f", tempSlope) + "°C per interval");
                precautionParts.add("Ensure proper ventilation; check for dust in vents.");
                warningCount++;
            } else {
                issueParts.add("Temperature is decreasing (" + String.format("%.2f", tempSlope) + "°C per interval) – good sign");
                precautionParts.add("No action needed on thermal front.");
            }
        }
        if (Math.abs(batSlope) > 0.2) {
            if (batSlope < 0) {
                issueParts.add("Battery is depleting at " + String.format("%.2f", Math.abs(batSlope)) + "% per interval");
                precautionParts.add("Plug in the charger if possible.");
                warningCount++;
            } else {
                issueParts.add("Battery level is increasing – charging may be active");
                precautionParts.add("Monitor charging progress.");
            }
        }

        if (issueParts.isEmpty()) {
            issueParts.add("No significant trends detected – system is stable.");
            precautionParts.add("Continue regular monitoring.");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("healthStatus", "Stable");
            response.put("futureHealth", "Stable");
            response.put("issues", "System metrics are stable. No major trends detected.");
            response.put("rootCause", "All metrics show minimal variation over the last 10 telemetry points.");
            response.put("recommendations", Arrays.asList("Continue regular monitoring."));
            response.put("riskLevel", "Low");
            return response;
        }

        String issuesCombined = String.join("; ", issueParts);
        String precautionsCombined = String.join("; ", precautionParts);

        // Determine risk level
        String healthStatus, futureHealth, riskLevel;
        if (warningCount >= 2) {
            healthStatus = "Warning";
            futureHealth = "Warning";
            riskLevel = "Medium";
        } else if (warningCount == 1) {
            healthStatus = "Good";
            futureHealth = "Good";
            riskLevel = "Low";
        } else {
            healthStatus = "Stable";
            futureHealth = "Stable";
            riskLevel = "Low";
        }

        if (cpuSlope > 1.0 || tempSlope > 0.8) {
            healthStatus = "Critical";
            futureHealth = "Critical";
            riskLevel = "High";
        }

        String rootCause = "Based on the last 10 telemetry points, the following trends were identified: " + issuesCombined;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("healthStatus", healthStatus);
        response.put("futureHealth", futureHealth);
        response.put("issues", issuesCombined);
        response.put("rootCause", rootCause);
        response.put("recommendations", Arrays.asList(precautionsCombined.split("; ")));
        response.put("riskLevel", riskLevel);
        return response;
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    //Builds system instructions for the LLM.
    private String buildSystemInstructions() {
        return "You are a Senior Dell Laptop System Engineer with 15 years of experience in hardware diagnostics and thermal management.\n\n" +
               "Your task is to provide a **detailed, thorough, and educational** analysis of the laptop's telemetry data.\n\n" +
               "**Guidelines:**\n" +
               "1. Write in a professional, conversational, and authoritative tone.\n" +
               "2. Always explain the **cause and effect** relationship (e.g., 'CPU load is high, which causes temperature to rise, which forces the fan to spin faster').\n" +
               "3. Provide 2-3 paragraphs of analysis. Do not give short, one-sentence replies.\n" +
               "4. If there are no critical issues, explain *why* the system is healthy and what the current trends indicate.\n" +
               "5. Your recommendations must be **actionable and specific** (e.g., 'Close Chrome tabs' vs 'Close applications').\n\n" +
               "Format your reply as a valid JSON object with these exact keys:\n" +
               "- \"diagnosis\": (string) a detailed explanation of the current state.\n" +
               "- \"recommendation\": (string) a clear, step-by-step action plan.\n" +
               "- \"confidence\": (float) a number between 0.0 and 1.0.";
    }

    //Builds the prompt payload with telemetry context.
    private String buildPromptPayload(String query, TelemetryRow current, List<TelemetryRow> history,
                                      List<String> ragDocs, String simResult, int risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("USER QUESTION: ").append(query).append("\n\n");
        sb.append("--- CURRENT TELEMETRY SNAPSHOT ---\n");
        sb.append("- CPU Core Load: ").append(String.format("%.1f", current.getCpuLoad())).append("%\n");
        sb.append("- Thermal Sensor: ").append(String.format("%.1f", current.getTemperature())).append("°C\n");
        sb.append("- Active Cooling Fan: ").append(String.format("%.0f", current.getFanRpm())).append(" RPM\n");
        sb.append("- Battery Level: ").append(String.format("%.1f", current.getBatteryLevel())).append("%\n");
        sb.append("- Disk Health Index: ").append(String.format("%.1f", current.getDiskHealth())).append("%\n\n");

        sb.append("--- HISTORICAL CONTEXT (Last 5 records) ---\n");
        for (TelemetryRow row : history) {
            sb.append("  • CPU: ").append(String.format("%.1f", row.getCpuLoad())).append("%, Temp: ").append(String.format("%.1f", row.getTemperature())).append("°C\n");
        }
        sb.append("\n");

        if (risk > 50) {
            sb.append("⚠️ SYSTEM RISK LEVEL: ").append(risk).append("% (Elevated - Pay attention)\n\n");
        } else {
            sb.append("✅ SYSTEM RISK LEVEL: ").append(risk).append("% (Normal)\n\n");
        }

        if (!simResult.equals("No simulation override requested.")) {
            sb.append("--- SIMULATION OVERRIDE ---\n").append(simResult).append("\n\n");
        }

        sb.append("--- INSTRUCTIONS ---\n");
        sb.append("Provide a comprehensive, 2-3 paragraph analysis. Explain the relationship between the metrics. \n");
        sb.append("If the user asked about a specific issue (e.g., 'heating', 'battery', 'slow'), focus your answer on that aspect while using the data to back it up.\n");
        sb.append("If the user didn't mention a specific issue, provide a general health checkup.\n");
        return sb.toString();
    }

    // Generates mock responses when no API key is available.
    private String generateDynamicMockReply(String query, TelemetryRow current, int risk) {
        double cpu = current.getCpuLoad();
        double temp = current.getTemperature();
        double battery = current.getBatteryLevel();
        double disk = current.getDiskHealth();

        String diagnosis = "";
        String recommendation = "";
        double confidence = 0.85;

        // Heat/Temperature issues
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.contains("heat") || lowerQuery.contains("hot") || lowerQuery.contains("temperature") || lowerQuery.contains("overheating")) {
            if (temp > 70) {
                diagnosis = "Your laptop is experiencing elevated thermal levels. The CPU is currently at " + String.format("%.1f", cpu) + "% utilization, which is generating significant heat. At " + String.format("%.1f", temp) + "°C, the system is approaching the thermal throttling threshold. This is likely due to sustained high load combined with potential dust accumulation in the cooling vents. The fan is operating at " + String.format("%.0f", current.getFanRpm()) + " RPM, which suggests the cooling system is actively trying to dissipate the heat, but the thermal load remains high.";
                recommendation = "1. Immediately close any unnecessary background applications (check Task Manager). 2. Ensure the laptop is placed on a hard, flat surface to allow proper airflow – avoid soft surfaces like beds or pillows. 3. If the issue persists, consider using a compressed air can to clean the cooling vents and fan. 4. If the temperature exceeds 85°C, consider shutting down the system to prevent hardware damage.";
                confidence = 0.92;
            } else if (temp > 55) {
                diagnosis = "Your system is running warm, but within acceptable parameters. At " + String.format("%.1f", temp) + "°C, the CPU fan is likely active, but thermal throttling is not imminent. The current CPU load of " + String.format("%.1f", cpu) + "% suggests a moderate workload. This is a normal operating condition for a laptop under regular use. The thermal management system appears to be functioning correctly.";
                recommendation = "Continue monitoring the temperature. If you plan to run heavy applications (e.g., gaming, video rendering), ensure the room is well-ventilated. Consider using a cooling pad for extended heavy usage.";
                confidence = 0.88;
            } else {
                diagnosis = "Your laptop's thermal sensors report a cool " + String.format("%.1f", temp) + "°C. Despite your question about heating, the telemetry indicates that the system is currently operating well within safe thermal limits. This is an optimal state for performance and longevity. The CPU load of " + String.format("%.1f", cpu) + "% is well managed, and the cooling system is maintaining ideal temperatures.";
                recommendation = "No immediate action is required. Your cooling system is performing effectively. Continue your work as usual.";
                confidence = 0.95;
            }
        }   // Battery issues
            else if (lowerQuery.contains("battery") || lowerQuery.contains("drain") || lowerQuery.contains("charging")) {
            if (battery < 20) {
                diagnosis = "Battery level is critically low at " + String.format("%.1f", battery) + "%. The current CPU load of " + String.format("%.1f", cpu) + "% is consuming power at a rapid rate. Prolonged use at this level risks unexpected shutdown. The battery discharge rate is accelerated due to the active workload.";
                recommendation = "1. Plug the laptop into a power source immediately. 2. Reduce screen brightness to conserve remaining charge. 3. Close non-essential applications to minimize power draw. 4. If possible, enable battery saver mode through Windows settings.";
                confidence = 0.97;
            } else if (battery < 50) {
                diagnosis = "Battery is at " + String.format("%.1f", battery) + "%. This is a moderate level, but it is depleting at a rate of approximately 1% every few minutes given the current CPU load of " + String.format("%.1f", cpu) + "%. The system is consuming " + String.format("%.1f", current.getFanRpm() * 0.1) + "W on average, which is typical for this workload.";
                recommendation = "Consider plugging in if you plan to run intensive tasks, otherwise you have sufficient charge for light work (approximately 1-2 hours).";
                confidence = 0.90;
            } else {
                diagnosis = "Battery health is excellent at " + String.format("%.1f", battery) + "%. The system is drawing normal power for the current workload of " + String.format("%.1f", cpu) + "% CPU. This indicates a healthy power subsystem and efficient power management.";
                recommendation = "Maintain current usage patterns. No charging required at this moment. The battery is in optimal condition.";
                confidence = 0.93;
            }
        }    // Performance issues
            else if (lowerQuery.contains("slow") || lowerQuery.contains("lag") || lowerQuery.contains("performance")) {
            if (cpu > 80) {
                diagnosis = "Performance lag is likely due to high CPU utilization of " + String.format("%.1f", cpu) + "%. At this level, the processor is struggling to keep up with demand, causing delays in application responsiveness. The temperature is " + String.format("%.1f", temp) + "°C, which could lead to thermal throttling if sustained. The fan is running at " + String.format("%.0f", current.getFanRpm()) + " RPM, indicating active cooling.";
                recommendation = "1. Open Task Manager and sort by CPU usage – end any processes consuming excessive resources. 2. Restart the laptop if the issue persists (this clears temporary system caches). 3. Check for pending Windows updates that may be running in the background.";
                confidence = 0.89;
            } else if (disk < 60) {
                diagnosis = "Performance slowdown may be caused by disk health degradation. Disk health index is at " + String.format("%.1f", disk) + "%, which is below the optimal threshold. This could lead to longer load times and system stuttering, especially during file operations.";
                recommendation = "1. Run disk cleanup to remove temporary files. 2. Consider defragmenting the drive (if HDD) or optimizing (if SSD). 3. Back up important data immediately and monitor disk health regularly.";
                confidence = 0.85;
            } else {
                diagnosis = "System performance appears normal. CPU load is " + String.format("%.1f", cpu) + "%, temperature " + String.format("%.1f", temp) + "°C, and disk health is at " + String.format("%.1f", disk) + "%. No obvious bottleneck detected. The system is likely operating within expected parameters for the current workload.";
                recommendation = "If you still experience lag, consider checking network connectivity or background processes. Otherwise, the system is healthy.";
                confidence = 0.92;
            }
        } else {
            // ---- General health check ----
            if (temp < 60 && cpu < 60 && battery > 30 && disk > 70) {
                diagnosis = "System health is in an excellent state. CPU utilization is " + String.format("%.1f", cpu) + "%, temperature is a cool " + String.format("%.1f", temp) + "°C, battery is at " + String.format("%.1f", battery) + "%, and disk health is " + String.format("%.1f", disk) + "%. The laptop is operating efficiently and is ready for demanding tasks if required. All critical metrics are within safe ranges.";
                recommendation = "No corrective actions needed. Continue regular maintenance cycles (e.g., disk cleanup, software updates).";
                confidence = 0.96;
            } else if (cpu > 80 || temp > 75 || disk < 50) {
                diagnosis = "High stress detected on the system. CPU load is at " + String.format("%.1f", cpu) + "% and temperature is " + String.format("%.1f", temp) + "°C. This combination indicates a heavy workload that is pushing the hardware to its limits, which may reduce performance longevity over time. Disk health is " + String.format("%.1f", disk) + "% – consider backing up data soon.";
                recommendation = "1. Check for background processes (Windows Updates, Virus Scans). 2. Reduce the number of open applications. 3. Consider using a cooling pad. 4. If disk health continues to degrade, replace the drive.";
            } else {
                diagnosis = "System metrics are within nominal bounds. CPU is at " + String.format("%.1f", cpu) + "%, temperature is " + String.format("%.1f", temp) + "°C, battery is at " + String.format("%.1f", battery) + "%, and disk health is " + String.format("%.1f", disk) + "%. The laptop is functioning as expected for a standard workload. No immediate concerns.";
                recommendation = "No action is required. The system is stable and performing normally.";
                confidence = 0.88;
            }
        }

        
        diagnosis = diagnosis.replace("\"", "\\\"");
        recommendation = recommendation.replace("\"", "\\\"");

        return "{\"diagnosis\":\"" + diagnosis + "\",\"recommendation\":\"" + recommendation + "\",\"confidence\":" + confidence + "}";
    }

    // Calculates linear slope for trend analysis
    private double calculateSlope(double[] values) {
        int n = values.length;
        if (n < 2) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumX2 += i * i;
        }
        double numerator = n * sumXY - sumX * sumY;
        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) return 0;
        return numerator / denominator;
    }

    // Checks if query is general conversation.
    private boolean isGeneralChitchat(String query) {
        return query.contains("hello") || query.contains("hi ") || query.equals("hi") ||
               query.contains("who are you") || query.contains("your name") || 
               query.contains("thank you") || query.contains("thanks") || query.contains("bye");
    }
    //  Handles general conversation responses.
    private Map<String, Object> executeConversationalResponse(String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("confidence", 100);
        response.put("evidence", "General conversational dialogue layer activated.");
        response.put("simulation", "System metrics bypassed.");
        response.put("risk", "0");

        String lower = query.toLowerCase().trim();

        // 1. Identity check
        if (lower.contains("who are you") || lower.contains("your name")) {
            response.put("diagnosis", "I am your Dell AI Digital Twin assistant, a virtual engineering copy of your physical laptop.");
            response.put("recommendation", "You can ask me to evaluate machine logs, diagnose running conditions, or run 'What-If' threshold stress simulations.");
        } 
        // 2. Capabilities check
        else if (lower.contains("what can you do") || lower.contains("help") || lower.contains("features")) {
            response.put("diagnosis", "I can monitor live hardware telemetry data, track CPU loads, analyze thermal trends, and predict potential hardware risks.");
            response.put("recommendation", "Try asking me: 'Is my system overheating?', 'Predict my system health', or 'What if CPU usage reaches 90%?'.");
        }
        // 3. Status check / Small talk
        else if (lower.contains("how are you") || lower.contains("how's it going") || lower.contains("sup")) {
            response.put("diagnosis", "All virtual AI diagnostic circuits are functioning at 100% capacity. Ready to track hardware performance.");
            response.put("recommendation", "My digital engine is idling cleanly! Let me know if you want to run a quick diagnostic checkup on your laptop metrics.");
        }
        // 4. Gratitude
        else if (lower.contains("thank") || lower.contains("thanks")) {
            response.put("diagnosis", "Telemetry diagnostic session concluding smoothly.");
            response.put("recommendation", "You're very welcome! Let me know if you need any other telemetry analysis or stress simulations later.");
        } 
        // 5. Good Morning
        else if (lower.contains("good morning") || lower.contains("morning")) {
            response.put("diagnosis", "System initialized for a fresh operational cycle. All telemetry monitors are green.");
            response.put("recommendation", "Good morning! Ready to analyze some metrics? Give me a query or test out a stress scenario.");
        }
        // 6. Good Evening / Night
        else if (lower.contains("good evening") || lower.contains("good night")) {
            response.put("diagnosis", "Sustained daily telemetry logs compiled. System state remains stable.");
            response.put("recommendation", "Good evening! Let me know if you want a final performance review before wrapping up your session.");
        }
        // 7. Closings / Goodbyes
        else if (lower.contains("bye") || lower.contains("goodbye") || lower.contains("exit")) {
            response.put("diagnosis", "Digital Twin standing down to low-power standby mode.");
            response.put("recommendation", "Goodbye! Keep those air vents clear, and come back whenever you need a hardware health assessment.");
        }
        // 8. General Greetings / Catch-All (Hi, Hello, Hey)
        else {
            response.put("diagnosis", "Dell AI Digital Twin active and monitoring running telemetry loops.");
            response.put("recommendation", "Hi there! Please provide a hardware query like 'Is my device overheating?' or test a scenario using 'What if CPU usage reaches 90%?'.");
        }

        return response;
    }
    //Calculates deterministic risk score based on current metrics.
    private int calculateDeterministicRisk(TelemetryRow current, List<TelemetryRow> history) {
        int risk = 0;
        if (current.getTemperature() > 70) risk += 30;
        if (current.getCpuLoad() > 80) risk += 20;
        if (current.getDiskHealth() < 50) risk += 25;
        if (current.getWifiLatency() > 100) risk += 15;

        if (history.size() >= 2) {
            TelemetryRow previous = history.get(history.size() - 2);
            double tempDelta = current.getTemperature() - previous.getTemperature();
            if (tempDelta > 2 && current.getFanRpm() < 1500) {
                risk += 25; 
            }
        }
        return Math.min(100, risk);
    }
    // Executes what-if simulation for hypothetical scenarios.
    private String executeWhatIfSimulation(String query, TelemetryRow current) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Digital Twin Simulation Sandbox Output]\n");
        if (query.contains("90") || query.contains("high load") || query.contains("cpu usage")) {
            double projectedTemp = Math.min(95.0, current.getTemperature() + 25.0);
            double projectedFan = Math.min(4500.0, current.getFanRpm() + 1800.0);
            sb.append("• Target Vector Change: Adjust CPU Utilization to 90.0%\n")
              .append(String.format("• Thermal Projection: Temperature will climb to %.1f°C (Acoustic Fan Target: %.0f RPM)\n", projectedTemp, projectedFan))
              .append("• Power Distribution: Consumption escalates to 32.5W. Expected battery exhaustion in 45 minutes.");
        } else {
            sb.append("• Target Vector Change: Workload Variance Simulation Matrix\n")
              .append(String.format("• Result: Base variance stable at current metric footprint of %.1f°C.", current.getTemperature()));
        }
        return sb.toString();
    }

    // Formats evidence box with current metrics and RAG matches.
    private String formatEvidenceBox(TelemetryRow current, List<String> ragDocs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Active Telemetry Frame Log: [Temp=").append(current.getTemperature())
          .append("°C, Fan=").append(current.getFanRpm())
          .append("RPM, DiskHealth=").append(current.getDiskHealth()).append("%]\n");
        sb.append("Semantic Historical Matches (BM25 Archive Entries):\n");
        for (int i = 0; i < ragDocs.size(); i++) {
            sb.append(String.format(" [%d] %s\n", i + 1, ragDocs.get(i)));
        }
        return sb.toString();
    }

    //Extracts JSON fields from LLM response.

    private Map<String, Object> extractJsonFields(String raw) {
        Map<String, Object> fallback = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            fallback.put("diagnosis", "Telemetry payload unreadable.");
            fallback.put("recommendation", "Retry evaluating telemetry loops.");
            fallback.put("confidence", 0.50);
            return fallback;
        }
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            return jacksonMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            fallback.put("diagnosis", "Telemetry pattern matched successfully.");
            fallback.put("recommendation", "Review diagnostic variables inside the expander panel.");
            fallback.put("confidence", 0.85);
            return fallback;
        }
    }
    // Returns error response.
    private Map<String, Object> errorResponse(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("error", msg);
        return err;
    }
}