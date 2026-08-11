package com.dell.twin;

public class Constants {

    public static final String[] TELEMETRY_COLUMNS = {
        "timestamp",
        "cpuLoad",
        "temperature",
        "fanRpm",
        "batteryLevel",
        "wifiLatency",
        "diskHealth"
    };

    public static final String SUPABASE_URL
            = System.getenv("SUPABASE_URL");

    public static final String SUPABASE_KEY
            = System.getenv("SUPABASE_KEY");

    public static final String GROQ_API_KEY
            = System.getenv("GROQ_API_KEY");

    public static final String TABLE_TELEMETRY = "telemetry";
    public static final String TABLE_EMBEDDINGS = "embeddings";
}
