package com.dell.twin;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import okhttp3.*;
import com.google.gson.Gson;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class CsvMapperAndUploader {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String SUPABASE_URL = dotenv.get("SUPABASE_URL");
    private static final String SUPABASE_KEY = dotenv.get("SUPABASE_KEY");
    private static final String TABLE_NAME = "telemetry";

    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException, CsvException {
        String inputFile = "dell_like_laptop_telemetry_1000_rows-1.csv";
        List<TelemetryRow> originalRows = readCsv(inputFile);
        System.out.println("Read " + originalRows.size() + " rows from CSV.");

        List<TelemetryRow> finalRows = new ArrayList<>(originalRows);
        Random rand = new Random(42);

        for (TelemetryRow row : originalRows) {
            TelemetryRow newRow = new TelemetryRow();
            LocalDateTime dt = LocalDateTime.parse(row.timestamp, DateTimeFormatter.ISO_DATE_TIME);
            newRow.timestamp = dt.plusMinutes(5).format(DateTimeFormatter.ISO_DATE_TIME);
            newRow.cpuLoad = addNoise(row.cpuLoad, 0.02, rand);
            newRow.temperature = addNoise(row.temperature, 0.02, rand);
            newRow.fanRpm = addNoise(row.fanRpm, 0.02, rand);
            newRow.batteryLevel = addNoise(row.batteryLevel, 0.02, rand);
            newRow.wifiLatency = addNoise(row.wifiLatency, 0.02, rand);
            newRow.diskHealth = addNoise(row.diskHealth, 0.02, rand);
            finalRows.add(newRow);
        }

        System.out.println("Augmented to " + finalRows.size() + " rows.");
        saveCsv(finalRows, "telemetry_final.csv");
        uploadToSupabase(finalRows);
        System.out.println("Upload complete!");
    }

    private static List<TelemetryRow> readCsv(String filename) throws IOException, CsvException {
        try (CSVReader reader = new CSVReader(new FileReader(filename))) {
            List<String[]> lines = reader.readAll();
            List<TelemetryRow> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i);
                TelemetryRow row = new TelemetryRow();
                row.timestamp = cols[0];
                row.cpuLoad = Double.parseDouble(cols[1]);
                row.temperature = Double.parseDouble(cols[4]);
                row.fanRpm = Double.parseDouble(cols[5]);
                row.batteryLevel = Double.parseDouble(cols[6]);
                row.wifiLatency = Double.parseDouble(cols[9]);
                double diskUsage = Double.parseDouble(cols[8]);
                row.diskHealth = Math.max(0, 100 - diskUsage);
                rows.add(row);
            }
            return rows;
        }
    }

    private static void saveCsv(List<TelemetryRow> rows, String filename) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filename))) {
            writer.writeNext(new String[]{"timestamp", "cpu_load", "temperature", "fan_rpm", "battery_level", "wifi_latency", "disk_health"});
            for (TelemetryRow row : rows) {
                writer.writeNext(new String[]{row.timestamp, String.valueOf(row.cpuLoad), String.valueOf(row.temperature), String.valueOf(row.fanRpm), String.valueOf(row.batteryLevel), String.valueOf(row.wifiLatency), String.valueOf(row.diskHealth)});
            }
        }
        System.out.println("Saved " + rows.size() + " rows to " + filename);
    }

    private static double addNoise(double value, double factor, Random rand) {
        double noise = rand.nextGaussian() * factor * value;
        return Math.max(0, value + noise);
    }

    private static void uploadToSupabase(List<TelemetryRow> rows) throws IOException {
        List<Map<String, Object>> records = rows.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", row.timestamp);
            map.put("cpu_load", row.cpuLoad);
            map.put("temperature", row.temperature);
            map.put("fan_rpm", row.fanRpm);
            map.put("battery_level", row.batteryLevel);
            map.put("wifi_latency", row.wifiLatency);
            map.put("disk_health", row.diskHealth);
            return map;
        }).collect(Collectors.toList());

        String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME;
        String json = gson.toJson(records);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code() + " - " + response.body().string());
            }
            System.out.println("Uploaded " + rows.size() + " rows to Supabase.");
        }
    }
}