package com.dell.twin;

import java.util.List;

public class csvPlayer {
    private List<TelemetryRow> data;
    private int index = 0;

    public csvPlayer(List<TelemetryRow> data) {
        this.data = data;
    }

    public TelemetryRow getCurrentTelemetry() {
        return data.get(index);
    }

    public void next() {
        if (index < data.size() - 1) {
            index++;
        }
    }
}