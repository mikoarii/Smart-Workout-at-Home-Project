package com.example.smartworkoutathome_v4;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class summary_screen extends AppCompatActivity {

    private TextView tvTotalSession, tvTotalReps, tvFilter;
    private RecyclerView rvHistory;
    private View emptyState;

    private List<WorkoutData> allWorkouts = new ArrayList<>();
    private List<WorkoutData> filteredWorkouts = new ArrayList<>();
    private HistoryAdapter adapter;

    private String currentFilter = "ALL";
    private File workoutFile;

    public static class WorkoutData {
        private String type;
        private int reps;
        private int duration;
        private long timestamp;
        private String date;

        public WorkoutData() {}

        public WorkoutData(JSONObject obj) {
            try {
                this.type = obj.getString("type");
                this.reps = obj.getInt("reps");
                this.duration = obj.getInt("duration");
                this.timestamp = obj.getLong("timestamp");
                this.date = obj.optString("date", "");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public String getType() { return type; }
        public int getReps() { return reps; }
        public int getDuration() { return duration; }
        public long getTimestamp() { return timestamp; }
        public String getDate() { return date; }

        public void setType(String type) { this.type = type; }
        public void setReps(int reps) { this.reps = reps; }
        public void setDuration(int duration) { this.duration = duration; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public void setDate(String date) { this.date = date; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary_screen);

        // BACA DARI FOLDER DOWNLOAD
        File folder = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "WorkoutHistory");
        workoutFile = new File(folder, "data_latihan.json");

        initViews();
        setupRecyclerView();
        loadSummary();
    }

    private void initViews() {
        tvTotalSession = findViewById(R.id.tvTotalSession);
        tvTotalReps = findViewById(R.id.tvTotalReps);
        tvFilter = findViewById(R.id.tvFilter);
        rvHistory = findViewById(R.id.rvHistory);
        emptyState = findViewById(R.id.emptyState);
        tvFilter.setOnClickListener(v -> showFilterDialog());
    }

    private void setupRecyclerView() {
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(filteredWorkouts);
        rvHistory.setAdapter(adapter);
    }

    private void loadSummary() {
        try {
            if (!workoutFile.exists()) {
                showEmptyState();
                Toast.makeText(this,
                        "Belum ada data latihan\nSilakan selesaikan workout dulu!",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String content = bacaFile();
            if (content == null || content.trim().isEmpty() || content.equals("[]")) {
                showEmptyState();
                return;
            }

            JSONArray workoutsArray = new JSONArray(content);
            allWorkouts.clear();
            int totalReps = 0;

            for (int i = 0; i < workoutsArray.length(); i++) {
                JSONObject obj = workoutsArray.getJSONObject(i);
                WorkoutData w = new WorkoutData(obj);
                allWorkouts.add(w);
                totalReps += w.getReps();
            }

            Collections.sort(allWorkouts, (w1, w2) -> Long.compare(w2.getTimestamp(), w1.getTimestamp()));

            tvTotalSession.setText(String.valueOf(allWorkouts.size()));
            tvTotalReps.setText(String.valueOf(totalReps));

            applyFilter(currentFilter);

            Toast.makeText(this,
                    "📊 " + allWorkouts.size() + " data ditemukan",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Gagal memuat data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            showEmptyState();
        }
    }

    private String bacaFile() {
        try {
            FileInputStream fis = new FileInputStream(workoutFile);
            byte[] data = new byte[(int) workoutFile.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "[]";
        }
    }

    private void showEmptyState() {
        tvTotalSession.setText("0");
        tvTotalReps.setText("0");
        filteredWorkouts.clear();
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(View.VISIBLE);
        rvHistory.setVisibility(View.GONE);
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        tvFilter.setText(filter);

        filteredWorkouts.clear();

        if (filter.equals("ALL")) {
            filteredWorkouts.addAll(allWorkouts);
        }
        else if (filter.equals("PUSH_UP")) {
            for (WorkoutData w : allWorkouts) {
                if (w.getType().equals("PUSH_UP")) {
                    filteredWorkouts.add(w);
                }
            }
        }
        else if (filter.equals("SQUAT")) {
            for (WorkoutData w : allWorkouts) {
                if (w.getType().equals("SQUAT")) {
                    filteredWorkouts.add(w);
                }
            }
        }

        emptyState.setVisibility(filteredWorkouts.isEmpty() ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(filteredWorkouts.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void showFilterDialog() {
        String[] filters = {"ALL", "PUSH_UP", "SQUAT"};

        new AlertDialog.Builder(this)
                .setTitle("Filter")
                .setItems(filters, (dialog, which) -> applyFilter(filters[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<WorkoutData> list;

        public HistoryAdapter(List<WorkoutData> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            WorkoutData w = list.get(position);
            if (w.getType() != null) {
                if (w.getType().equalsIgnoreCase("PUSH_UP") || w.getType().contains("PUSH")) {
                    h.tvIcon.setText("💪");
                    h.tvType.setText("Push-up");
                } else if (w.getType().equalsIgnoreCase("SQUAT")) {
                    h.tvIcon.setText("🦵");
                    h.tvType.setText("Squat");
                } else {
                    h.tvIcon.setText("🏋️");
                    h.tvType.setText(w.getType());
                }
            }
            h.tvReps.setText(w.getReps() + " Reps");

            if (w.getDate() != null && !w.getDate().isEmpty()) {
                h.tvDate.setText(w.getDate());
            } else {
                h.tvDate.setText(new SimpleDateFormat("dd-MM-yy HH:mm", Locale.getDefault())
                        .format(new Date(w.getTimestamp())));
            }

            h.tvDuration.setText(String.format("%d:%02d",
                    w.getDuration() / 60, w.getDuration() % 60));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon, tvType, tvReps, tvDate, tvDuration;
            ViewHolder(View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tvIcon);
                tvType = itemView.findViewById(R.id.tvType);
                tvReps = itemView.findViewById(R.id.tvReps);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvDuration = itemView.findViewById(R.id.tvDuration);
            }
        }
    }
}