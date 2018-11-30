package com.rizaldi.motion.sensor.model;

import java.util.Locale;

public class Acceleration {
    private final long timestamp;
    private final float x;
    private final float y;
    private final float z;

    public Acceleration(long timestamp, float x, float y, float z) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "timestamp=%d, x=%.3f, y=%.3f, z=%.3f", timestamp, x, y, z);
    }
}
