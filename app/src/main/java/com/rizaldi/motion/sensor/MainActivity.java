package com.rizaldi.motion.sensor;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.rizaldi.motion.sensor.model.Acceleration;
import com.rizaldi.motion.sensor.model.RoadType;
import com.rizaldi.motion.sensor.util.MotionAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import de.siegmar.fastcsv.writer.CsvAppender;
import de.siegmar.fastcsv.writer.CsvWriter;

public class MainActivity extends Activity implements SensorEventListener {
    // Android Permission Request Code Identifier
    private static final int WRITE_CSV_RC = 4345;
    private static final int FILE_PICKER_RC = 35345;
    private static final int FILE_PICKER_PRC = 23123;
    private final CsvReader reader;
    private final CsvWriter writer;
    private final List<Acceleration> accelerations;
    private final AtomicReference<ScheduledExecutorService> realTimePredictExecutor;
    private final AtomicBoolean isRecording;
    private final AtomicInteger realTimeOffset;
    // Android Service Component
    private ClipboardManager clipboardManager;
    private TextView predictText;
    private SensorManager sensorManager;
    private Sensor sensor;
    // Android Ui Component
    private TextView loadingText;
    private TextView rtPredictText;

    public MainActivity() {
        reader = new CsvReader();
        reader.setContainsHeader(true);
        writer = new CsvWriter();
        accelerations = Collections.synchronizedList(new ArrayList<>(1000000));
        realTimePredictExecutor = new AtomicReference<>(Executors.newSingleThreadScheduledExecutor());
        isRecording = new AtomicBoolean(false);
        realTimeOffset = new AtomicInteger(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initServiceComponent();
        initUiComponent();
        initUiListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecord();
    }

    private void initServiceComponent() {
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    private void initUiComponent() {
        loadingText = findViewById(R.id.loadingText);
        predictText = findViewById(R.id.predictText);
        rtPredictText = findViewById(R.id.rtPredictText);
    }

    private void initUiListener() {
        findViewById(R.id.toggleButton).setOnClickListener(this::onClickToggle);
        findViewById(R.id.copyButton).setOnClickListener(this::onClickCopy);
        findViewById(R.id.importButton).setOnClickListener(this::onClickImport);
        findViewById(R.id.exportButton).setOnClickListener(this::onClickExport);
        findViewById(R.id.predictButton).setOnClickListener(this::onClickPredict);
    }

    private void onClickToggle(View v) {
        if (isRecording.get()) stopRecord();
        else startRecord();
    }

    private void onClickCopy(View v) {
        ClipData data = ClipData.newPlainText("sensor", generateSpreadsheetLog());
        clipboardManager.setPrimaryClip(data);
    }

    private void onClickPredict(View v) {
        RoadType road = MotionAnalyzer.predictRoad(accelerations);
        predictText.setText(road.name());
    }

    private void startRecord() {
        accelerations.clear();
        isRecording.set(true);
        realTimeOffset.set(0);

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);

        realTimePredictExecutor.get().scheduleAtFixedRate(this::realTimePredict, 2, 2, TimeUnit.SECONDS);

        loadingText.setText("recording...");
    }

    private void stopRecord() {
        isRecording.set(false);

        sensorManager.unregisterListener(this, sensor);

        realTimePredictExecutor.get().shutdownNow();
        realTimePredictExecutor.set(Executors.newSingleThreadScheduledExecutor());

        loadingText.setText("finish record.");
    }

    private void realTimePredict() {
        List<Acceleration> realTimeData = accelerations.subList(realTimeOffset.get(), accelerations.size());
        realTimeOffset.addAndGet(realTimeData.size());

        RoadType road = MotionAnalyzer.predictRoad(realTimeData);
        rtPredictText.setText(road.name());
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
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
            new MaterialFilePicker()
                    .withActivity(this)
                    .withRequestCode(FILE_PICKER_RC)
                    .withTitle("Import CSV File")
                    .withFilter(Pattern.compile(".*\\.csv$"))
                    .withPath(path)
                    .start();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, FILE_PICKER_PRC);
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
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            exportCsv();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, WRITE_CSV_RC);
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
