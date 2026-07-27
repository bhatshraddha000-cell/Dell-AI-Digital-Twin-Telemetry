package com.dell.twin;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Scanner;

/**
 * CLI driver for the Dell AI Digital Twin.
 * Demonstrates loading telemetry, health scoring, issue diagnosis,
 * failure prediction, and interactive Q&A.
 */

public class main {
    public static void main(String[] args) throws Exception {
        // 1. Load all telemetry from CSV
        csvReader reader = new csvReader();
        List<TelemetryRow> data = reader.readFile("dell_like_laptop_telemetry_1000_rows-1.csv");

        System.out.println("Records Loaded: " + data.size());
        System.out.println(data.get(0));

        // 2. Simulate real-time playback for the first few records
        csvPlayer player = new csvPlayer(data);
        ArrayDeque<TelemetryRow> history = new ArrayDeque<>();

        for (int i = 0; i < Math.min(4, data.size()); i++) {
            TelemetryRow current = player.getCurrentTelemetry();
            history.addLast(current);
            if (history.size() > 3) {
                history.removeFirst();
            }

            int health = healthService.calculateHealth(current);
            String status = healthService.getHealthStatus(health);

            System.out.println("--------------------------------");
            System.out.println(current);
            System.out.println("Health Score: " + health);
            System.out.println("Status: " + status);
            player.next();
            Thread.sleep(1000);
        }

        // 3. Run issue diagnosis on the first row
        System.out.println("------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("Issue Diagnose........");
        issueDiagnose.issue(data.get(0));
        System.out.println();

         // 4. Run failure prediction using the last 3 records (history)
        System.out.println("Failure Prediction........");
        prediction.predict(history);

        // 5. Optional interactive chat
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter 'yes' if you want to ask anything or 'no': ");
        String ask = sc.nextLine();

        if (ask.equalsIgnoreCase("yes")) {
            System.out.println("AI Assistant........");
            chatBox.chat(data.get(0));
        }

        System.out.println("------------------------------------------------------------------------------------------------------------------------------------------------------------");
    }
}