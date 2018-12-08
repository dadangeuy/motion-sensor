package com.rizaldi.motion.sensor;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.rizaldi.motion.sensor.model.Acceleration;
import com.rizaldi.motion.sensor.util.MotionAnalyzer;
import com.rizaldi.motion.sensor.util.MotionAnalyzer.RoadType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import de.siegmar.fastcsv.writer.CsvAppender;
import de.siegmar.fastcsv.writer.CsvWriter;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int WRITE_CSV_RC = 4345;
    private static final int FILE_PICKER_RC = 35345;
    private static final int FILE_PICKER_PRC = 23123;
    private final List<Acceleration> accelerations = new ArrayList<>(1000000);
    private final CsvReader reader = new CsvReader();
    private final CsvWriter writer = new CsvWriter();
    private TextView loadingText;
    private TextView predictText;
    private ClipboardManager clipboardManager;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean isRecording = false;

    public MainActivity() {
        reader.setContainsHeader(true);
    }

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
        findViewById(R.id.importButton).setOnClickListener(this::onClickImport);
        findViewById(R.id.exportButton).setOnClickListener(this::onClickExport);
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
        ClipData data = ClipData.newPlainText("sensor", generateSpreadsheetLog());
        clipboardManager.setPrimaryClip(data);
    }

    private void onClickPredict(View v) {
        RoadType type = MotionAnalyzer.predictRoad(accelerations);
        predictText.setText(type.name());
    }

    private void startRecord() {
        isRecording = true;
        accelerations.clear();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        loadingText.setText("recording...");
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

    private String generateSpreadsheetLog() {
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

    private void onClickImport(View v) {
        checkPermissionsAndOpenFilePicker();
    }

    private void checkPermissionsAndOpenFilePicker() {
        String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
            new MaterialFilePicker()
                    .withActivity(this)
                    .withRequestCode(FILE_PICKER_RC)
                    .withTitle("Import CSV File")
                    .withFilter(Pattern.compile(".*\\.csv$"))
                    .withPath(path)
                    .start();
        } else {
            requestPermissions(new String[]{permission}, FILE_PICKER_PRC);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_RC && resultCode == RESULT_OK) {
            String path = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            importCsv(path);
        }
    }

    private void importCsv(String path) {
        File file = new File(path);
        try (CsvParser parser = reader.parse(file, StandardCharsets.UTF_8)) {
            accelerations.clear();
            CsvRow row;
            while ((row = parser.nextRow()) != null) {
                long ts = Long.valueOf(row.getField("ts"));
                float x = Float.valueOf(row.getField("x"));
                float y = Float.valueOf(row.getField("y"));
                float z = Float.valueOf(row.getField("z"));
                Acceleration acceleration = new Acceleration(ts, x, y, z);
                accelerations.add(acceleration);
            }
            Toast.makeText(this, String.format("%d data added", accelerations.size()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onClickExport(View v) {
        checkPermissionsAndExportCsv();
    }

    private void checkPermissionsAndExportCsv() {
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            exportCsv();
        } else {
            requestPermissions(new String[]{permission}, WRITE_CSV_RC);
        }
    }

    private void exportCsv() {
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(directory, "motion-dataset.csv");
        int id = 0;
        while (file.exists()) {
            file = new File(directory, String.format("motion-dataset (%d).csv", ++id));
        }
        try (CsvAppender appender = writer.append(file, StandardCharsets.UTF_8)) {
            appender.appendLine("ts", "x", "y", "z");
            for (Acceleration acceleration : accelerations) {
                appender.appendLine(
                        String.valueOf(acceleration.getTimestamp()),
                        String.valueOf(acceleration.getX()),
                        String.valueOf(acceleration.getY()),
                        String.valueOf(acceleration.getZ())
                );
            }
            Toast.makeText(this, "exported to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
