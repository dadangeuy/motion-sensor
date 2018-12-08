package com.rizaldi.motion.sensor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.rizaldi.motion.sensor.model.Acceleration;
import com.rizaldi.motion.sensor.util.MotionAnalyzer;
import com.rizaldi.motion.sensor.util.MotionAnalyzer.RoadType;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {
    private final List<Acceleration> accelerations = new ArrayList<>(1000000);
    private TextView loadingText;
    private TextView predictText;
    private ClipboardManager clipboardManager;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadingText = findViewById(R.id.loadingText);
        predictText = findViewById(R.id.predictText);

        clipboardManager = getSystemService(ClipboardManager.class);
        sensorManager = getSystemService(SensorManager.class);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        findViewById(R.id.toggleButton).setOnClickListener(this::onClickToggle);
        findViewById(R.id.copyButton).setOnClickListener(this::onClickCopy);
        findViewById(R.id.predictButton).setOnClickListener(this::onClickPredict);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecord();
    }

    private void onClickToggle(View v) {
        if (isRecording) stopRecord();
        else startRecord();
    }

    private void onClickCopy(View v) {
        ClipData data = ClipData.newPlainText("sensor", generateSpreadsheet());
        clipboardManager.setPrimaryClip(data);
    }

    private void onClickPredict(View v) {
        RoadType type = MotionAnalyzer.predictRoad(accelerations);
        predictText.setText(type.name());
    }

    private void startRecord() {
        isRecording = true;
        loadingText.setText("recording...");
        accelerations.clear();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }

    private void stopRecord() {
        isRecording = false;
        sensorManager.unregisterListener(this, sensor);
        loadingText.setText("finish record.");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Acceleration acceleration = new Acceleration(event);
        accelerations.add(acceleration);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private String generateSpreadsheet() {
        StringBuilder text = new StringBuilder();
        text.append("ts\tx\ty\tz");
        for (Acceleration acceleration : accelerations) {
            text.append('\n')
                    .append(acceleration.getTimestamp()).append('\t')
                    .append(acceleration.getX()).append('\t')
                    .append(acceleration.getY()).append('\t')
                    .append(acceleration.getZ());
        }
        return text.toString();
    }
}
