package com.dell.twin;

/**
 * Represents a single telemetry data point.
 * Fields: timestamp, cpuLoad, temperature, fanRpm, batteryLevel,
 * wifiLatency, diskHealth.
 * Provides constructors for both 7‑column and 10‑column CSV schemas.
 */

public class TelemetryRow {
    // Core fields – match the 7‑column schema
    public String timestamp;
    public double cpuLoad;
    public double temperature;
    public double fanRpm;
    public double batteryLevel;
    public double wifiLatency;
    public double diskHealth;

    /** Default constructor (required for Jackson/OpenCSV). */
    public TelemetryRow() {}

    /**
     * Constructor for 7‑column CSV (exact mapping).
     * @param timestamp    time of reading
     * @param cpuLoad      CPU usage (%)
     * @param temperature  temperature (°C)
     * @param fanRpm       fan speed (RPM)
     * @param batteryLevel battery percentage
     * @param wifiLatency  network latency (ms)
     * @param diskHealth   disk health percentage
     */
    public TelemetryRow(String timestamp, double cpuLoad, double temperature, double fanRpm,
                        double batteryLevel, double wifiLatency, double diskHealth) {
        this.timestamp = timestamp;
        this.cpuLoad = cpuLoad;
        this.temperature = temperature;
        this.fanRpm = fanRpm;
        this.batteryLevel = batteryLevel;
        this.wifiLatency = wifiLatency;
        this.diskHealth = diskHealth;
    }

    /**
     * Constructor for 10‑column CSV (maps to the 7 fields).
     * Assumes order: timestamp, cpuUsage, ramUsage, gpuUsage,
     * temperature, fanRpm, battery, powerWatts, diskUsage, networkMbps.
     * @param timestamp    time
     * @param cpuUsage     CPU usage (%) → cpuLoad
     * @param ramUsage     (ignored, not stored)
     * @param gpuUsage     (ignored, not stored)
     * @param temperature  → temperature
     * @param fanRpm       → fanRpm
     * @param battery      → batteryLevel
     * @param powerWatts   (ignored)
     * @param diskUsage    disk usage (%) → diskHealth = 100 - diskUsage
     * @param networkMbps  network speed (Mbps) → wifiLatency (interpreted as latency)
     */

    public TelemetryRow(String timestamp, double cpuUsage, double ramUsage, double gpuUsage,
                        double temperature, double fanRpm, double battery, double powerWatts,
                        double diskUsage, double networkMbps) {
        this.timestamp = timestamp;
        this.cpuLoad = cpuUsage;
        this.temperature = temperature;
        this.fanRpm = fanRpm;
        this.batteryLevel = battery;
        this.wifiLatency = networkMbps; // mapping network speed to latency (approx)
        this.diskHealth = 100 - diskUsage;
    }

    // Getters and Setters
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public double getCpuLoad() { return cpuLoad; }
    public void setCpuLoad(double cpuLoad) { this.cpuLoad = cpuLoad; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getFanRpm() { return fanRpm; }
    public void setFanRpm(double fanRpm) { this.fanRpm = fanRpm; }

    public double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(double batteryLevel) { this.batteryLevel = batteryLevel; }

    public double getWifiLatency() { return wifiLatency; }   // ✅ FIXED: Capital W
    public void setWifiLatency(double wifiLatency) { this.wifiLatency = wifiLatency; }

    public double getDiskHealth() { return diskHealth; }
    public void setDiskHealth(double diskHealth) { this.diskHealth = diskHealth; }  // ✅ FIXED: added = diskHealth

    @Override
    public String toString() {
        return String.format("Row[%s, CPU=%.1f, Temp=%.1f, Fan=%.0f, Bat=%.1f, WiFi=%.1f, Disk=%.1f]",
                timestamp, cpuLoad, temperature, fanRpm, batteryLevel, wifiLatency, diskHealth);
    }
}