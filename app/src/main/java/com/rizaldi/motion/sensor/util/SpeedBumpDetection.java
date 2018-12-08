package com.rizaldi.motion.sensor.util;

import java.util.List;

public class SpeedBumpDetection {
    private static float SPEED_BUMP_THRESHOLD = 1;

    public static boolean isSpeedBump(List<Long> ts, List<Float> zs) {
        long[] pts = new long[ts.size()];
        float[] pzs = new float[zs.size()];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = ts.get(i);
            pzs[i] = zs.get(i);
        }
        return isSpeedBump(pts, pzs);
    }

    public static boolean isSpeedBump(long[] ts, float[] zs) {
        float[] hs = new float[zs.length];
        hs[0] = zs[0] * ts[0];
        for (int i = 1; i < zs.length; i++) {
            long dt = ts[i] - ts[i - 1];
            hs[i] = (dt / 1000f) * zs[i] + hs[i - 1];
        }
        float initialHeight = hs[0];
        float minHeight = min(hs);
        float diffHeight = initialHeight - minHeight;
        return diffHeight > 1;
    }

    private static float min(float[] arr) {
        float min = arr[0];
        for (int i = 1; i < arr.length; i++) min = Math.min(min, arr[i]);
        return min;
    }
}
