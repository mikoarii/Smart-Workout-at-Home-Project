package com.example.smartworkoutathome_v4;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class save_screen extends AppCompatActivity {

    private String workoutType;
    private int totalReps;
    private long durationMillis;
    private boolean isSaving = false;
    private File workoutFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_screen);

        // BUAT FOLDER DI DOWNLOAD
        File folder = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "WorkoutHistory");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        workoutFile = new File(folder, "data_latihan.json");

        // Buat file kosong jika belum ada
        if (!workoutFile.exists()) {
            try {
                workoutFile.createNewFile();
                try (FileWriter writer = new FileWriter(workoutFile)) {
                    writer.write("[]");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        getIntentData();
        initUI();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            workoutType = intent.getStringExtra("WORKOUT_TYPE");
            totalReps = intent.getIntExtra("TOTAL_REPS", 0);
            durationMillis = intent.getLongExtra("DURATION", 0);
        }

        if (workoutType == null || totalReps <= 0) {
            Toast.makeText(this, "Tidak ada data workout", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initUI() {
        TextView tvWorkoutType = findViewById(R.id.tvWorkoutType);
        TextView tvTotalReps = findViewById(R.id.tvTotalReps);
        TextView tvDuration = findViewById(R.id.tvDuration);
        Button btnSave = findViewById(R.id.btnSave_backtohome);

        if (tvWorkoutType == null || tvTotalReps == null || tvDuration == null || btnSave == null) {
            Toast.makeText(this, "Error: Layout error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvWorkoutType.setText(workoutType != null ? workoutType.toUpperCase() : "SQUAT");
        tvTotalReps.setText(String.valueOf(totalReps));
        tvDuration.setText(formatDuration(durationMillis));

        btnSave.setOnClickListener(v -> saveWorkout());
    }

    private String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void saveWorkout() {
        if (isSaving) return;
        isSaving = true;

        try {
            String existingData = bacaFile();
            JSONArray workoutsArray;

            if (existingData == null || existingData.trim().isEmpty()) {
                workoutsArray = new JSONArray();
            } else {
                workoutsArray = new JSONArray(existingData);
            }

            JSONObject newWorkout = new JSONObject();
            newWorkout.put("type", workoutType.toUpperCase());
            newWorkout.put("reps", totalReps);
            newWorkout.put("duration", (int) (durationMillis / 1000));
            newWorkout.put("timestamp", System.currentTimeMillis());
            newWorkout.put("date", new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                    .format(new Date()));

            workoutsArray.put(newWorkout);

            try (FileWriter writer = new FileWriter(workoutFile)) {
                writer.write(workoutsArray.toString(2));
            }

            Toast.makeText(this,
                    "✅ Workout tersimpan!\n📁 Download/WorkoutHistory/data_latihan.json",
                    Toast.LENGTH_LONG).show();
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "❌ Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private String bacaFile() {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(workoutFile);
            byte[] data = new byte[(int) workoutFile.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "[]";
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        finish();
    }
}