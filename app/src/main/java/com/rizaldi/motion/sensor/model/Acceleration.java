package com.rizaldi.motion.sensor.model;

import android.hardware.SensorEvent;

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

    public Acceleration(SensorEvent event) {
        timestamp = event.timestamp;
        x = event.values[0];
        y = event.values[1];
        z = event.values[2];
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }
}
