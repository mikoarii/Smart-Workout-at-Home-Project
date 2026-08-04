# AIoT Smart Workout at Home 🏋️‍♂️🤖

> **Perancangan Sistem AIoT Smart Workout at Home Berbasis MediaPipe Pose dengan Penyelarasan Kamera Otomatis ESP32**
> 
> *Skripsi oleh: **Miko Ari Septiawan** (NIM: 2241160102)*  
> *Program Studi D-IV Jaringan Telekomunikasi Digital, Jurusan Teknik Elektro, Politeknik Negeri Malang (2026)*

---

## 📌 Deskripsi Proyek

**AIoT Smart Workout at Home** adalah sistem pelatihan kebugaran mandiri berbasis Artificial Intelligence of Things (AIoT). Sistem ini mengintegrasikan **MediaPipe Pose (BlazePose)** pada aplikasi Android untuk mendeteksi pose tubuh secara *real-time*, menghitung repetisi olahraga (**Push-Up** dan **Squat**), memberikan koreksi postur suara (*Text-to-Speech*), serta menyelaraskan sudut kamera secara otomatis menggunakan gimbal 2-axis berbasis **ESP32** dan servo **MG996R** melalui komunikasi nirkabel UDP.

---

## ✨ Fitur Utama

- 📐 **Pose Estimation & Joint Angle Calculation**: Menggunakan MediaPipe Pose untuk melacak 6 titik landmark utama sisi kanan tubuh (Bahu, Siku, Pergelangan Tangan, Pinggul, Lutut, Pergelangan Kaki) dan menghitung sudut sendi dengan metode *Dot Product*.
- 🔄 **Validasi Repetisi Berbasis FSM**: Menggunakan *Finite State Machine* (FSM) dengan aturan *Rule-Based Threshold* untuk memastikan gerakan valid (`UP` $\rightarrow$ `DOWN` $\rightarrow$ `UP`) dan mencegah *false count*[cite: 1].
- 🎯 **One-Tap Center / Auto Camera Alignment**: Penyelarasan posisi kamera secara otomatis agar tubuh pengguna selalu berada di tengah *frame* sebelum latihan dimulai, dipicu oleh tombol *UI* atau gestur tangan horizontal[cite: 1].
- 📡 **Komunikasi Nirkabel UDP**: Komunikasi cepat dan hemat *overhead* antara aplikasi Android (sebagai Hotspot) dan mikrokontroler ESP32 via protokol UDP (Port 4210)[cite: 1].
- 🔊 **Koreksi Form Real-Time (Text-to-Speech)**: Memberikan umpan balik suara instan saat postur pengguna salah (misal: pinggul terlalu rendah/tinggi atau lutut melampaui batas)[cite: 1].
- 📊 **Local Workout Storage & Summary**: Menyimpan riwayat latihan (jumlah repetisi, durasi, jenis latihan) secara lokal pada *storage* *smartphone*[cite: 1].

---

## 🛠️ Spesifikasi Hardware & Software

### Perangkat Keras (Hardware)
| Komponen | Spesifikasi / Keterangan |
| :--- | :--- |
| **Mikrokontroler** | ESP32 DEV KIT V1 |
| **Motor Servo** | 2x Servo Metal Gear MG996R (Pan & Tilt) |
| **Rangka Gimbal** | Dual Axis Pan-Tilt Bracket Servo + Smartphone Holder |
| **Power Supply** | Switching Power Supply 5V 10A (untuk Servo) & Adaptor 5V 2A (ESP32) |
| **Smartphone** | Android Device (Rekomendasi RAM $\ge$ 4GB, Kamera RGB)[cite: 1] |

### Perangkat Lunak (Software)
- **Android Studio** (Bahasa Java, CameraX API, MediaPipe Pose, TextToSpeech API, DatagramSocket UDP)[cite: 1]
- **Arduino IDE** (ESP32 Board Manager, `WiFi.h`, `WiFiUDP.h`, `ESP32Servo.h`)

---

## 🔌 Skema Koneksi & Wiring (ESP32)

| Komponen | Pin ESP32 | Sumber Daya |
| :--- | :--- | :--- |
| **Servo Pan (Horizontal)** | GPIO 18 (PWM) | VCC External 5V, GND Shared |
| **Servo Tilt (Vertikal)** | GPIO 19 (PWM) | VCC External 5V, GND Shared |
| **Power Supply 5V** | - | Connected to Servos VCC & Shared GND |

> ⚠️ **Penting**: Pastikan *Ground* (GND) dari ESP32 dan Power Supply 5V terhubung bersama (*Common Ground*).

---

## 🚀 Panduan Instalasi & Konfigurasi

### 1. Konfigurasi Firmware ESP32 (Arduino IDE)

1. Buka **Arduino IDE** dan pasang Board Support ESP32 via *Board Manager*.
2. Install pustaka `ESP32Servo` melalui *Library Manager*.
3. Buka file skrip ESP32 (`/esp32/smart_workout_gimbal.ino`).
4. Atur kredensial Wi-Fi Hotspot bawaan:
   ```cpp
   const char* ssid = "SmartWorkout";
   const char* password = "12345678";
   const unsigned int localUdpPort = 4210;
