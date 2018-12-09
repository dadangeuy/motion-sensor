package com.rizaldi.motion.sensor.util;

import com.rizaldi.motion.sensor.model.Acceleration;
import com.rizaldi.motion.sensor.model.MotionType;
import com.rizaldi.motion.sensor.model.RoadType;

import java.util.Collection;

public final class MotionAnalyzer {

    public static RoadType predictRoad(Collection<Acceleration> data) {
        return predictRoad(data.toArray(new Acceleration[0]));
    }

    public static RoadType predictRoad(Acceleration[] data) {
        double[] height = getHeightPosition(data);
        long durationNano = data[data.length - 1].getTimestamp() - data[0].getTimestamp();
        int durationSecond = Long.valueOf(durationNano / 1000000000).intValue();
        int dataPerSecond = 50;
        height = meanReduction(height, durationSecond * dataPerSecond);
        MotionType[] motions = classifyMotions(height, 1.0 / dataPerSecond);
        return classifyRoad(motions, 1.0 / dataPerSecond);
    }

    private static double[] getHeightPosition(Acceleration[] data) {
        double[] time = getTime(data);

        double[] move = new double[data.length];
        for (int i = 0; i < data.length; i++) move[i] = 0.5 * data[i].getZ() * time[i] * time[i];

        double[] position = new double[data.length];
        position[0] = move[0];
        for (int i = 1; i < data.length; i++) position[i] = move[i] + position[i - 1];

        return position;
    }

    private static double[] getTime(Acceleration[] data) {
        long[] timestamp = new long[data.length];
        for (int i = 0; i < data.length; i++)
            timestamp[i] = data[i].getTimestamp() - data[0].getTimestamp();

        double[] time = new double[data.length];
        time[0] = 0;
        for (int i = 1; i < data.length; i++) time[i] = (timestamp[i] - timestamp[i - 1]) * 1e-9;

        return time;
    }

    private static double[] meanReduction(double[] data, int partition) {
        double[] rdata = new double[partition];
        int length = (data.length + partition - 1) / partition;
        for (int i = 0, j = 0; i < data.length; i += length, j++) {
            double mean = meanRange(data, i, Math.min(data.length, i + length));
            rdata[j] = mean;
        }
        return rdata;
    }

    private static double meanRange(double[] data, int start, int end) {
        double mean = 0;
        int size = end - start;
        for (int i = start; i < end; i++) mean += data[i] / size;
        return mean;
    }

    private static MotionType[] classifyMotions(double[] height, double durationPerHeight) {
        MotionType[] motions = new MotionType[height.length];
        motions[0] = MotionType.STABLE;
        for (int i = 1; i < height.length; i++) {
            double riseRate = (height[i] - height[i - 1]) / durationPerHeight;
            if (-0.08 < riseRate && riseRate < 0.08) motions[i] = MotionType.STABLE;
            else if (riseRate > 0.08) motions[i] = MotionType.UP;
            else if (riseRate < -0.08) motions[i] = MotionType.DOWN;
            else motions[i] = MotionType.UNKNOWN;
        }
        return motions;
    }

    private static RoadType classifyRoad(MotionType[] motions, double durationPerMotion) {
        int up = count(motions, MotionType.UP);
        int down = count(motions, MotionType.DOWN);
        double bouncingDuration = (up + down) * durationPerMotion;

        if (bouncingDuration > 0.5) return RoadType.DAMAGED;
        else if (bouncingDuration > 0.1 && upThenDown(motions)) return RoadType.SPEED_BUMP;
        else if (bouncingDuration > 0.1 && downThenUp(motions)) return RoadType.POTHOLE;
        else return RoadType.NORMAL;
    }

    private static int count(MotionType[] motions, MotionType motion) {
        int count = 0;
        for (MotionType iMotion : motions)
            if (iMotion == motion)
                ++count;
        return count;
    }

    private static boolean upThenDown(MotionType[] motions) {
        int upIdx = findFirstIdx(motions, MotionType.UP);
        int downIdx = findFirstIdx(motions, MotionType.DOWN);
        return (upIdx != motions.length && upIdx < downIdx);
    }

    private static boolean downThenUp(MotionType[] motions) {
        int downIdx = findFirstIdx(motions, MotionType.DOWN);
        int upIdx = findFirstIdx(motions, MotionType.UP);
        return (downIdx != motions.length && downIdx < upIdx);
    }

    private static int findFirstIdx(MotionType[] motions, MotionType motion) {
        for (int i = 0; i < motions.length; i++)
            if (motions[i] == motion)
                return i;
        return motions.length;
    }
}
