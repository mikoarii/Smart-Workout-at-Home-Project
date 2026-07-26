package com.example.smartworkoutathome_v4;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class home_screen extends AppCompatActivity {

    private static final String TAG = "HomeScreen";
    private static final int PERMISSION_REQUEST_CODE = 100;

    //  ENGINE UDP BARU (Menggantikan Firebase/WebSocket lama untuk deteksi alat)
    private ESP32UDPManager udpManager;

    // UI Components (Murni mempertahankan struktur v2 kesukaanmu)
    private Button btnSummary, btnPushUp, btnSquat;
    private TextView tvHotspotStatus, tvDataStatus, tvEspStatus;
    private LinearLayout layoutKoneksi;

    // 3 Status Validasi Utama untuk Logika Tombol Latihan
    private boolean isInternetConnected = false;
    private boolean isHotspotActive = false;
    private boolean isEspConnected = false;

    // Handler Loop untuk pemantauan Status Infrastruktur Jaringan (Real-time per 3 detik)
    private Handler validationHandler = new Handler(Looper.getMainLooper());
    private Runnable validationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_screen);

        //  LINKING KOMPONEN - 100% Akurat sesuai ID Asli tatanan v2 milikmu
        btnPushUp = findViewById(R.id.pushup);
        btnSquat = findViewById(R.id.squat);
        btnSummary = findViewById(R.id.riwayat);

        tvHotspotStatus = findViewById(R.id.tvHotspotStatus);
        tvDataStatus = findViewById(R.id.tvDataStatus);
        tvEspStatus = findViewById(R.id.tvEspStatus);
        layoutKoneksi = findViewById(R.id.layout_koneksi);

        // Kunci tombol latihan di awal demi keamanan sistem sebelum validasi sukses
        setWorkoutButtonsEnabled(false);

        // Cek runtime permissions perangkat Android
        checkAndRequestPermissions();

        // Inisialisasi UDP Engine Manager Singleton
        udpManager = ESP32UDPManager.getInstance();

        //  VALIDASI LAYER 3: Deteksi balasan Alat langsung dari sinyal biner udara via LiveData UDP
        udpManager.getConnectionState().observe(this, state -> {
            isEspConnected = (state == ESP32UDPManager.ConnectionState.CONNECTED);

            // Ubah text & warna indikator ESP32 murni dari respons hardware sesungguhnya
            if (isEspConnected) {
                tvEspStatus.setText("TERHUBUNG");
                tvEspStatus.setTextColor(0xFF4CAF50); // Hijau Sukses
            } else {
                tvEspStatus.setText("MENUNGGU");
                tvEspStatus.setTextColor(0xFFFF9800); // Kuning/Jingga Standby
            }

            evaluateAllValidations(); // Evaluasi gabungan kuncian tombol
        });

        // Jalankan background monitoring untuk Validasi Layer 1 (Internet) dan Layer 2 (Hotspot)
        startNetworkAndHotspotMonitor();

        // Tombol Riwayat/Summary (Bisa diakses kapan saja tanpa kuncian hardware)
        btnSummary.setOnClickListener(v -> {
            Intent intent = new Intent(home_screen.this, summary_screen.class);
            startActivity(intent);
        });

        // Proteksi Tombol Push-Up dengan 3 Validasi Berlapis
        btnPushUp.setOnClickListener(v -> {
            if (isInternetConnected && isHotspotActive && isEspConnected) {
                Intent intent = new Intent(home_screen.this, centering_workout_screen.class);
                intent.putExtra("WORKOUT_TYPE", "PUSH_UP");
                startActivity(intent);
            } else {
                triggerWarningToast();
            }
        });

        // Proteksi Tombol Squat dengan 3 Validasi Berlapis
        btnSquat.setOnClickListener(v -> {
            if (isInternetConnected && isHotspotActive && isEspConnected) {
                Intent intent = new Intent(home_screen.this, centering_workout_screen.class);
                intent.putExtra("WORKOUT_TYPE", "SQUAT");
                startActivity(intent);
            } else {
                triggerWarningToast();
            }
        });
    }

    // ============================================================
    // EVALUASI AKHIR GABUNGAN 3 VALIDASI (GERBANG PENGUNCI TOMBOL)
    // ============================================================
    private void evaluateAllValidations() {
        if (isInternetConnected && isHotspotActive && isEspConnected) {
            // JIKA SEMUA SIAP: Buka gembok tombol latihan, buat menyala penuh
            btnPushUp.setEnabled(true);
            btnSquat.setEnabled(true);
            btnPushUp.setAlpha(1.0f);
            btnSquat.setAlpha(1.0f);
        } else {
            // JIKA ADA YANG BELUM SIAP: Kunci tombol, buat transparan samar-samar
            btnPushUp.setEnabled(false);
            btnSquat.setEnabled(false);
            btnPushUp.setAlpha(0.4f);
            btnSquat.setAlpha(0.4f);
        }
    }

    private void setWorkoutButtonsEnabled(boolean enabled) {
        btnPushUp.setEnabled(enabled);
        btnSquat.setEnabled(enabled);
        btnPushUp.setAlpha(enabled ? 1.0f : 0.4f);
        btnSquat.setAlpha(enabled ? 1.0f : 0.4f);
    }

    // ============================================================
    // BACKGROUND CHECKER INFRASTRUKTUR JARINGAN INTERNAL HP
    // ============================================================

    //  VALIDASI LAYER 1: Cek Koneksi Data Seluler/Wifi untuk Keperluan Firebase Database
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) return false;

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    //  VALIDASI LAYER 2: Cek Apakah Tethering Hotspot HP Sedang Aktif Menyala
    private boolean isWifiHotspotEnabled() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            method.setAccessible(true);
            int state = (Integer) method.invoke(wifiManager);
            return (state == 13); // Konstanta internal Android 13 berarti WIFI_AP_STATE_ENABLED
        } catch (Exception e) {
            return true; // Fallback jika sistem keamanan OS Android membatasi refleksi API
        }
    }

    private void startNetworkAndHotspotMonitor() {
        validationRunnable = new Runnable() {
            @Override
            public void run() {
                // Ambil status riil jaringan saat ini
                isInternetConnected = isNetworkAvailable();
                isHotspotActive = isWifiHotspotEnabled();

                // Perbarui Teks & Warna Indikator Data Seluler secara real-time
                if (isInternetConnected) {
                    tvDataStatus.setText("TERHUBUNG");
                    tvDataStatus.setTextColor(0xFF4CAF50); // Hijau
                } else {
                    tvDataStatus.setText("TERPUTUS");
                    tvDataStatus.setTextColor(0xFFF44336); // Merah
                }

                // Perbarui Teks & Warna Indikator Hotspot HP secara real-time
                if (isHotspotActive) {
                    tvHotspotStatus.setText("AKTIF");
                    tvHotspotStatus.setTextColor(0xFF4CAF50); // Hijau
                } else {
                    tvHotspotStatus.setText("MATI");
                    tvHotspotStatus.setTextColor(0xFFF44336); // Merah
                }

                evaluateAllValidations();

                // Lakukan looping terus-menerus secara asinkron setiap 3 detik sekali
                validationHandler.postDelayed(this, 3000);
            }
        };
        validationHandler.post(validationRunnable);
    }

    private void triggerWarningToast() {
        StringBuilder sb = new StringBuilder("Akses Ditangguhkan! Periksa Kembali:\n");
        if (!isInternetConnected) sb.append("• Hidupkan Data Seluler (Firebase Cloud)\n");
        if (!isHotspotActive) sb.append("• Aktifkan Tethering Hotspot HP\n");
        if (!isEspConnected) sb.append("• Hidupkan Saklar ESP32 & Tunggu Respons");

        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
    }

    // ============================================================
    // PERMISSIONS & LIFECYCLE MANAGEMENT
    // ============================================================
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        permissionsNeeded.add(Manifest.permission.CAMERA);
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        List<String> listPermissionsAssign = new ArrayList<>();
        for (String perm : permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsAssign.add(perm);
            }
        }

        if (!listPermissionsAssign.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsAssign.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Izin ditolak perangkat: " + permissions[i]);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (udpManager != null) {
            udpManager.connect(); // Bangunkan socket broadcast kembali saat user kembali ke halaman utama
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (validationHandler != null && validationRunnable != null) {
            validationHandler.removeCallbacks(validationRunnable);
        }
        if (udpManager != null) {
            udpManager.cleanup(); // Hancurkan socket thread agar tidak terjadi kebocoran memori (memory leak)
        }
    }
}