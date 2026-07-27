package com.dell.twin;

/**
 * Utility class for computing a simple health score and status.
 * Health degrades with high CPU, high temperature, and low battery.
 */

public class healthService {

    /**
     * Calculates health score (0-100) based on telemetry.
     * Penalties: CPU/5, Temperature/4, (100 - Battery)/5.
     * @param t the telemetry row
     * @return health score, clamped to [0,100]
     */

    public static int calculateHealth(TelemetryRow t) {
        int score = 100;
        score -= t.getCpuLoad() / 5;
        score -= t.getTemperature() / 4;
        score -= (100 - t.getBatteryLevel()) / 5;
        if (score < 0) score = 0;
        return score;
    }

    /**
     * Converts a numeric score to a descriptive status.
     * @param score health score (0-100)
     * @return "Excellent", "Good", "Warning", or "Critical"
     */

    public static String getHealthStatus(int score) {
        if (score >= 80) return "Excellent";
        if (score >= 60) return "Good";
        if (score >= 40) return "Warning";
        return "Critical";
    }
}