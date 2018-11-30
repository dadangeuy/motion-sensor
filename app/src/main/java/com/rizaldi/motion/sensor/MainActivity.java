package com.rizaldi.motion.sensor;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rizaldi.motion.sensor.model.Acceleration;

public class MainActivity extends Activity implements SensorEventListener {
    private TextView previewText;
    private Button toggleButton;
    private Button clearButton;
    private ScrollView scrollView;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleButton = findViewById(R.id.toggleButton);
        clearButton = findViewById(R.id.clearButton);
        previewText = findViewById(R.id.previewText);
        scrollView = findViewById(R.id.scrollView);
        sensorManager = getSystemService(SensorManager.class);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        toggleButton.setOnClickListener(this::onClickToggleButton);
        clearButton.setOnClickListener(this::onClickClearButton);
    }

    private void onClickToggleButton(View v) {
        if (isRecording) stopRecord();
        else startRecord();
    }

    private void startRecord() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        isRecording = true;
    }

    private void stopRecord() {
        sensorManager.unregisterListener(this, sensor);
        isRecording = false;
    }

    private void onClickClearButton(View v) {
        previewText.setText("");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Acceleration acceleration = new Acceleration(
                event.timestamp,
                event.values[0],
                event.values[1],
                event.values[2]
        );
        previewText.append(acceleration.toString() + '\n');
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
