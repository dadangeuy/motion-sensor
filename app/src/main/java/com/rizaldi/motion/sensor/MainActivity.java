package com.rizaldi.motion.sensor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import de.siegmar.fastcsv.writer.CsvAppender;
import de.siegmar.fastcsv.writer.CsvWriter;

@SuppressLint({"DefaultLocale", "MissingPermission", "SetTextI18n"})
public class MainActivity extends Activity implements SensorEventListener {
    // Android Request Code Identifier
    private static final int FILE_PICKER_RC = 35345;
    // Internal Field
    private final CsvReader reader;
    private final CsvWriter writer;
    private final List<Acceleration> accelerations;
    private final AtomicReference<ScheduledExecutorService> realTimePredictExecutor;
    private final AtomicBoolean isRecording;
    // Android Service Component
    private ClipboardManager clipboardManager;
    private SensorManager sensorManager;
    private Sensor sensor;
    private FusedLocationProviderClient locationProvider;
    private RequestQueue requestQueue;
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initServiceComponent();
        initUiComponent();
        initUiListener();
        initPermission();
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
        locationProvider = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(this);
    }

    private void initUiComponent() {
        loadingText = findViewById(R.id.loadingText);
        rtPredictText = findViewById(R.id.rtPredictText);
    }

    private void initUiListener() {
        findViewById(R.id.toggleButton).setOnClickListener(this::onClickToggle);
        findViewById(R.id.copyButton).setOnClickListener(this::onClickCopy);
        findViewById(R.id.importButton).setOnClickListener(this::onClickImport);
        findViewById(R.id.exportButton).setOnClickListener(this::onClickExport);
    }

    private void initPermission() {
        String[] permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET
        };
        ActivityCompat.requestPermissions(this, permissions, 0);
    }

    private void onClickToggle(View v) {
        if (isRecording.get()) stopRecord();
        else startRecord();
    }

    private void onClickCopy(View v) {
        ClipData data = ClipData.newPlainText("sensor", generateSpreadsheetLog());
        clipboardManager.setPrimaryClip(data);
    }

    private void startRecord() {
        accelerations.clear();
        isRecording.set(true);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        realTimePredictExecutor.get().scheduleAtFixedRate(this::realTimePredict, 3, 3, TimeUnit.SECONDS);
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
        RoadType road = MotionAnalyzer.predictRoad(accelerations);
        accelerations.clear();
        rtPredictText.setText(road.name());
        if (road == RoadType.NORMAL) return;
        locationProvider.getLastLocation()
                .addOnSuccessListener((location) ->
                        savePrediction(road, location.getLatitude(), location.getLongitude()));
    }

    private void savePrediction(RoadType road, double latitude, double longitude) {
        Map<String, String> form = new HashMap<>();
        form.put("jenis", road.name());
        form.put("lintang", String.valueOf(latitude));
        form.put("bujur", String.valueOf(longitude));

        StringRequest request = new StringRequest(
                Request.Method.POST,
                "http://fpkomber.herokuapp.com/uploadkomber",
                (response) -> notifySuccess(road),
                this::notifyError) {
            @Override
            protected Map<String, String> getParams() {
                return form;
            }
        };
        requestQueue.add(request);
    }

    private void notifySuccess(RoadType road) {
        Toast.makeText(this,
                road.name() + " saved",
                Toast.LENGTH_SHORT).show();
    }

    private void notifyError(VolleyError error) {
        Toast.makeText(this,
                String.format("failed to save (%d)", error.networkResponse.statusCode),
                Toast.LENGTH_SHORT).show();
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
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
        new MaterialFilePicker()
                .withActivity(this)
                .withRequestCode(FILE_PICKER_RC)
                .withTitle("Import CSV File")
                .withFilter(Pattern.compile(".*\\.csv$"))
                .withPath(path)
                .start();
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
            Toast.makeText(this,
                    String.format("%d data added", accelerations.size()),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this,
                    e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onClickExport(View v) {
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
