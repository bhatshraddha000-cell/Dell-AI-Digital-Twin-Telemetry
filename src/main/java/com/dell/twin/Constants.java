package com.dell.twin;

import io.github.cdimascio.dotenv.Dotenv;

public class Constants {
    // Load .env file
    private static final Dotenv dotenv = Dotenv.configure().load();

    // ---------- STRICT COLUMN NAMES (Case Sensitive) ----------
    public static final String[] TELEMETRY_COLUMNS = {
        "timestamp", "cpuLoad", "temperature", "fanRpm", "batteryLevel", "wifiLatency", "diskHealth"
    };

    // ---------- DATABASE & API KEYS ----------
    public static final String SUPABASE_URL = dotenv.get("SUPABASE_URL");
    public static final String SUPABASE_KEY = dotenv.get("SUPABASE_KEY");
    public static final String GROQ_API_KEY = dotenv.get("GROQ_API_KEY");

    // ---------- TABLE NAMES ----------
    public static final String TABLE_TELEMETRY = "telemetry";
    public static final String TABLE_EMBEDDINGS = "embeddings";
}