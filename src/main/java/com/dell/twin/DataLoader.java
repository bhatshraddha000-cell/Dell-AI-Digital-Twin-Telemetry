package com.dell.twin;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataLoader {

    private List<TelemetryRow> telemetryData = new ArrayList<>();

    // Constructor loads data immediately
    public DataLoader() {
        loadData();
    }

    private void loadData() {
        String csvFile = "dell_like_laptop_telemetry_1000_rows-1.csv";
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            List<String[]> lines = reader.readAll();
            // Skip header
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i);
                TelemetryRow row = new TelemetryRow();
                row.setTimestamp(cols[0]);
                row.setCpuLoad(Double.parseDouble(cols[1]));
                row.setTemperature(Double.parseDouble(cols[4]));
                row.setFanRpm(Double.parseDouble(cols[5]));
                row.setBatteryLevel(Double.parseDouble(cols[6]));
                row.setWifiLatency(Double.parseDouble(cols[9]));
                double diskUsage = Double.parseDouble(cols[8]);
                row.setDiskHealth(100 - diskUsage);
                telemetryData.add(row);
            }
            System.out.println("Loaded " + telemetryData.size() + " telemetry records.");
        } catch (IOException | CsvException e) {
            System.err.println("Failed to load CSV: " + e.getMessage());
        }
    }

    public List<TelemetryRow> getAllData() {
        return telemetryData;
    }

    // Get the most recent row (for "current" state)
    public TelemetryRow getLatest() {
        if (telemetryData.isEmpty()) return new TelemetryRow();
        return telemetryData.get(telemetryData.size() - 1);
    }

    // Get rows around a specific time (for context)
    public List<TelemetryRow> getContext(int count) {
        if (telemetryData.isEmpty()) return new ArrayList<>();
        int size = telemetryData.size();
        int start = Math.max(0, size - count);
        return telemetryData.subList(start, size);
    }
}