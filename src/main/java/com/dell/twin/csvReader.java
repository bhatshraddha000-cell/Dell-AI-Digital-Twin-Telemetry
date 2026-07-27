package com.dell.twin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class csvReader {

    public List<TelemetryRow> readFile(String filePath) {
        List<TelemetryRow> telemetryList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                String timestamp = data[0];
                double cpuUsage = Double.parseDouble(data[1]);
                double ramUsage = Double.parseDouble(data[2]);
                double gpuUsage = Double.parseDouble(data[3]);
                double temperature = Double.parseDouble(data[4]);
                double fanRpm = Double.parseDouble(data[5]);
                double battery = Double.parseDouble(data[6]);
                double powerWatts = Double.parseDouble(data[7]);
                double diskUsage = Double.parseDouble(data[8]);
                double networkMbps = Double.parseDouble(data[9]);

                TelemetryRow t = new TelemetryRow(timestamp, cpuUsage, ramUsage, gpuUsage,
                        temperature, fanRpm, battery, powerWatts, diskUsage, networkMbps);
                telemetryList.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return telemetryList;
    }
}