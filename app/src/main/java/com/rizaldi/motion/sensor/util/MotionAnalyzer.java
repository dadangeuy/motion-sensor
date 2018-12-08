package com.rizaldi.motion.sensor.util;

import com.rizaldi.motion.sensor.model.Acceleration;

import java.util.List;

public class MotionAnalyzer {
    private static double SPEED_BUMP_THRESHOLD = 0.002;
    private static double POTHOLE_THRESHOLD = 0.002;
    private static double DURATION_THRESHOLD = 0.1;

    public static RoadType predictRoad(List<Acceleration> accelerations) {
        return predictRoad(accelerations.toArray(new Acceleration[0]));
    }

    public static RoadType predictRoad(Acceleration[] accelerations) {
        double[] times = calculateTimes(accelerations);
        double[] heights = calculateHeights(times, accelerations);

        double initialHeightTime = times[0];
        double initialHeight = heights[0];
        for (int i = 1; i < heights.length; i++) {
            double height = heights[i];
            double time = times[i];
            if ((time - initialHeightTime) > DURATION_THRESHOLD) {
                initialHeightTime = time;
                initialHeight = height;
            }

            double rise = height - initialHeight;
            if (rise > SPEED_BUMP_THRESHOLD) return RoadType.SPEED_BUMP;
            else if (rise < 0 && Math.abs(rise) > POTHOLE_THRESHOLD) return RoadType.POTHOLE;
        }
        return RoadType.NORMAL;
    }

    private static double[] calculateTimes(Acceleration[] accelerations) {
        double[] times = new double[accelerations.length];
        long startTimeNano = accelerations[0].getTimestamp();
        for (int i = 0; i < accelerations.length; i++) {
            long timeNano = accelerations[i].getTimestamp() - startTimeNano;
            float timeSecond = timeNano / 1000000000f;
            times[i] = timeSecond;
        }
        return times;
    }

    private static double[] calculateHeights(double[] times, Acceleration[] accelerations) {
        double[] distances = new double[accelerations.length];
        distances[0] = 0;
        for (int i = 1; i < accelerations.length; i++) {
            double deltaTime = times[i] - times[i - 1];
            double distance = distance(accelerations[i].getZ(), deltaTime);
            distances[i] = distance;
        }
        double[] heights = new double[accelerations.length];
        heights[0] = 0;
        for (int i = 1; i < accelerations.length; i++) {
            heights[i] = heights[i - 1] + distances[i];
        }
        return heights;
    }

    private static double distance(double acceleration, double time) {
        return 0.5 * acceleration * time * time;
    }

    public enum RoadType {
        NORMAL, SPEED_BUMP, POTHOLE, DAMAGED
    }
}
