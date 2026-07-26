package com.example.smartworkoutathome_v4;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.media.Image;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class centering_workout_screen extends AppCompatActivity {

    private static final String TAG = "CenteringWorkoutScreen";
    private static final int CAMERA_PERMISSION_CODE = 200;

    // ==================== UDP MANAGER ====================
    private ESP32UDPManager udpManager;
    private boolean isUdpConnected = false;
    private Handler centeringHandler = new Handler();

    // ==================== STATE MANAGEMENT ====================
    private enum AppState { CENTERING, WORKOUT }
    private AppState currentState = AppState.CENTERING;

    // ==================== LANDMARK INDEX ====================
    private static final int RIGHT_SHOULDER = 12;
    private static final int RIGHT_ELBOW = 14;
    private static final int RIGHT_WRIST = 16;
    private static final int RIGHT_HIP = 24;
    private static final int RIGHT_KNEE = 26;
    private static final int RIGHT_ANKLE = 28;

    // ==================== PARAMETER CENTERING ====================
    private static final float POSITION_TOLERANCE = 0.08f;
    private static final int STABLE_FRAMES_REQUIRED = 5;

    // ==================== PARAMETER JARAK (TTS) ====================
    private static final float MIN_IDEAL_BOX_SIZE = 0.18f;
    private static final float MAX_IDEAL_BOX_SIZE = 0.55f;
    private static final float TARGET_BOX_SIZE = 0.32f;

    // ==================== PARAMETER SERVO ====================
    private static final int PAN_MIN = 30;
    private static final int PAN_MAX = 150;
    private static final int TILT_MIN = 50;
    private static final int TILT_MAX = 110;
    private static final int DEAD_ZONE_PX = 1;

    // ==================== PARAMETER WORKOUT ====================
// PUSH-UP - LAPIS 1
    private static final float PUSHUP_DOWN_MIN = 30f;
    private static final float PUSHUP_DOWN_MAX = 110f;
    private static final float PUSHUP_UP_THRESHOLD = 130f;
    private static final float TORSO_TILT_THRESHOLD_PUSHUP = 80f;

    // PUSH-UP - LAPIS 2 (Koreksi)
    private static final float PUSHUP_TOO_DEEP = 60f;
    private static final float PUSHUP_TOO_SHALLOW = 110f;

    // SQUAT - LAPIS 1
    private static final float SQUAT_DOWN_MIN = 80f;
    private static final float SQUAT_DOWN_MAX = 90f;
    private static final float SQUAT_UP_THRESHOLD = 140f;
    private static final float TORSO_TILT_THRESHOLD_SQUAT = 40f;

    // SQUAT - LAPIS 2 (Koreksi)
    private static final float SQUAT_TOO_DEEP = 70f;
    private static final float SQUAT_TOO_SHALLOW = 120f;

    // ==================== COUNTDOWN ====================
    private static final int PREPARE_COUNTDOWN_SECONDS = 5;
    private static final int HOLD_COUNTDOWN_SECONDS = 2;

    // ==================== TTS COOLDOWN ====================
    private static final long DISTANCE_TTS_COOLDOWN = 5000;
    private static final long TTS_COOLDOWN = 1500;

    // ==================== UI COMPONENTS ====================
    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView tvInstruction, tvStatus, tvCountdown, tvCorrection, tvZoneInfo;
    private TextView tvWorkoutType, tvRepsCount, tvPosition, tvTimer;
    private Button btnOneTapCenter, btnStop;

    // ==================== KAMERA & MEDIAPIPE ====================
    private ExecutorService cameraExecutor;
    private PoseLandmarker poseLandmarker;
    private boolean isProcessing = false;
    private boolean isWorkoutActive = false;
    private int frameCounter = 0;
    private static final int FRAME_SKIP = 1;

    // ==================== CENTERING STATE ====================
    private boolean isCenteringActive = false;
    private boolean isPositionCentered = false;
    private boolean isFrozen = false;
    private int stableCount = 0;
    private String workoutType = "SQUAT";
    private float smoothedCenterX = 0.5f, smoothedCenterY = 0.5f;
    private boolean hasSmoothedData = false;
    private int framesWithoutDetection = 0;
    private float currentBoxHeight = 0f;
    private long lastDistanceTtsTime = 0;
    private float lastErrorX = 0f;
    private float lastErrorY = 0f;

    private float[] memoryHip = new float[]{0.5f, 0.5f};  // Posisi hip yang diingat [x, y]
    private boolean hasMemory = false;                    // Apakah memory sudah terisi?
    private static final float CENTER_TOLERANCE = 0.03f;   // 3% - indikator masuk kotak
    private static final float TRACKING_GAIN = 22f;
    private static final int MAX_TRACKING_STEP = 5;

    // ==================== WORKOUT STATE ====================
    private int totalReps = 0;
    private long startTime = 0;
    private long elapsedTime = 0;
    private long pausedElapsedTime = 0;
    private enum WorkoutState { UP, DOWN }
    private WorkoutState workoutCurrentState = WorkoutState.UP;
    private boolean wasInDownState = false;
    private boolean isValidRep = false;
    private long lastRepTime = 0;
    private float smoothedElbowAngle = 0f;
    private float smoothedKneeAngle = 0f;
    private float smoothedTorsoAngle = 0f;

    // ==================== DEBOUNCE & STABILIZER ====================
    private long lastStateChangeTime = 0;
    private static final long STATE_DEBOUNCE_MS = 300;
    private int consecutiveInRange = 0;
    private static final int MIN_CONSECUTIVE = 3;

    // ==================== CENTERING WAITING ====================
    private boolean isWaitingPeriod = false;
    private static final int WAITING_PERIOD_MS = 2000;

    // ==================== RATE LIMITING & THRESHOLD ====================
    private long lastSendTime = 0;
    private static final int MIN_SEND_INTERVAL_MS = 150;
    private static final int SEND_THRESHOLD = 0;

    private int lastSentPan = 90;
    private int lastSentTilt = 90;

    // ==================== TTS ====================
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private long lastTtsTime = 0;
    private long lastCorrectionTime = 0;
    private static final long CORRECTION_COOLDOWN = 2500;
    private String lastCorrection = "";
    private static final long SAME_CORRECTION_COOLDOWN = 6000;
    private String pendingCorrection = "";
    private int pendingCorrectionCount = 0;
    private static final int CORRECTION_STABLE_FRAMES_REQUIRED = 3;

    // ==================== GESTUR CENTERING ====================
    private static final long GESTURE_COOLDOWN_MS = 3000;
    private long lastGestureTime = 0;
    private static final int MIN_ARM_LENGTH = 60;
    private CountDownTimer workoutTimer;

    // ==================== RE-CENTERING STATE ====================
    private boolean isReCenteringMode = false;

    // ==================== FPS COUNTER ====================
    private long lastFpsTime = 0;
    private int frameCount = 0;
    private float currentFps = 0f;

    // ==================== LIFECYCLE ====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_centering_workout_screen);

        workoutType = getIntent().getStringExtra("WORKOUT_TYPE");
        if (workoutType == null) workoutType = "SQUAT";
        Log.d(TAG, "Workout Type: " + workoutType);

        initUI();
        initTTS();
        initUDP();
        initPoseLandmarker();
        requestCameraPermission();
        updateUIForState();
    }

    private void initUI() {
        previewView = findViewById(R.id.previewView);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvStatus = findViewById(R.id.tvStatus);
        tvCountdown = findViewById(R.id.tvCountdown);
        tvCorrection = findViewById(R.id.tvCorrection);
        tvZoneInfo = findViewById(R.id.tvZoneInfo);
        tvWorkoutType = findViewById(R.id.tvWorkoutType);
        tvRepsCount = findViewById(R.id.tvRepsCount);
        tvPosition = findViewById(R.id.tvPosition);
        tvTimer = findViewById(R.id.tvTimer);
        btnOneTapCenter = findViewById(R.id.btnOneTapCenter);
        btnStop = findViewById(R.id.btnStop);

        overlayView = new OverlayView(this);
        ViewGroup parent = (ViewGroup) previewView.getParent();
        if (parent != null) {
            parent.addView(overlayView);
            overlayView.bringToFront();
        }

        btnOneTapCenter.setOnClickListener(v -> startCenteringProcess());
        btnStop.setOnClickListener(v -> stopWorkout());
        cameraExecutor = Executors.newSingleThreadExecutor();

        tvWorkoutType.setText(workoutType);
        if (workoutType.equalsIgnoreCase("PUSH_UP")) {
            tvWorkoutType.setTextColor(Color.parseColor("#FF9800"));
        } else {
            tvWorkoutType.setTextColor(Color.parseColor("#4CAF50"));
        }

        tvRepsCount.setText("0");
        tvPosition.setText("UP");
        tvTimer.setText("00:00");
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("id", "ID"));
                isTtsReady = true;
                Log.d(TAG, "TTS Ready");
            }
        });
    }

    private void speak(String text) {
        if (isTtsReady && tts != null) {
            long now = System.currentTimeMillis();
            if (now - lastTtsTime >= TTS_COOLDOWN) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                lastTtsTime = now;
            }
        }
    }

    private void speakDistance(String text) {
        if (isTtsReady && tts != null && !isFrozen) {
            long now = System.currentTimeMillis();
            if (now - lastDistanceTtsTime >= DISTANCE_TTS_COOLDOWN) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                lastDistanceTtsTime = now;
            }
        }
    }

    // ==================== UDP METHODS ====================
    private void initUDP() {
        // Gunakan Singleton
        udpManager = ESP32UDPManager.getInstance();

        // PERBAIKAN: Langsung ambil status koneksi dari manager
        isUdpConnected = udpManager.isConnected();
        Log.d(TAG, "Initial UDP connection status from manager: " + isUdpConnected);

        // Jika sudah terhubung, langsung jalankan unfreeze dan center
        if (isUdpConnected) {
            runOnUiThread(() -> {
                unfreezeServo();
                sendCenter();
            });
        }

        udpManager.setConnectionListener(new ESP32UDPManager.ConnectionListener() {
            @Override
            public void onConnected() {
                isUdpConnected = true;
                Log.d(TAG, "UDP Connected to ESP32 (from listener)");
                runOnUiThread(() -> {
                    unfreezeServo();
                    sendCenter();
                });
            }

            @Override
            public void onDisconnected() {
                isUdpConnected = false;
                Log.d(TAG, "UDP Disconnected from ESP32");
                runOnUiThread(() -> {
                    Toast.makeText(centering_workout_screen.this,
                            "Koneksi ke ESP32 terputus", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "UDP Message: " + message);

                // 🔥 TAMBAHKAN LOG INI (RESPON ESP32)
                if (message != null && message.startsWith("REACHED:")) {
                    String[] parts = message.substring(8).split(",");
                    if (parts.length == 2) {
                        try {
                            int pan = Integer.parseInt(parts[0].trim());
                            int tilt = Integer.parseInt(parts[1].trim());
                            boolean isMatch = (Math.abs(pan - lastSentPan) <= 2 && Math.abs(tilt - lastSentTilt) <= 2);
                            Log.d(TAG, String.format(Locale.US,
                                    "[PARAM1_ALIGNMENT] RESPONSE_PAN:%d RESPONSE_TILT:%d | TARGET_PAN:%d TARGET_TILT:%d | MATCH:%s",
                                    pan, tilt, lastSentPan, lastSentTilt, isMatch ? "YES" : "NO"
                            ));
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing REACHED message: " + message);
                        }
                    }
                }
            }
        });
    }

    private void sendPanTilt(int pan, int tilt) {
        sendPanTilt(pan, tilt, false);
    }

    private void sendPanTilt(int pan, int tilt, boolean forceSend) {
        if (udpManager == null || !isUdpConnected) return;

        long now = System.currentTimeMillis();

        pan = Math.max(PAN_MIN, Math.min(PAN_MAX, pan));
        tilt = Math.max(TILT_MIN, Math.min(TILT_MAX, tilt));

        int panDiff = Math.abs(pan - lastSentPan);
        int tiltDiff = Math.abs(tilt - lastSentTilt);

        boolean significantChange = (panDiff > SEND_THRESHOLD || tiltDiff > SEND_THRESHOLD);
        boolean enoughTime = (now - lastSendTime) >= MIN_SEND_INTERVAL_MS;

        if (forceSend || (significantChange && enoughTime)) {
            udpManager.moveServo(pan, tilt);
            lastSendTime = now;
            lastSentPan = pan;
            lastSentTilt = tilt;

            // PARAM1 LOG
            float errorX = memoryHip[0] - 0.5f;
            float errorY = memoryHip[1] - 0.5f;
            float offX = Math.abs(errorX);
            float offY = Math.abs(errorY);
            float accuracy = (1.0f - Math.max(offX, offY) / 0.45f) * 100f;
            if (accuracy < 0) accuracy = 0;
            if (accuracy > 100) accuracy = 100;

            String status = (offX <= CENTER_TOLERANCE && offY <= CENTER_TOLERANCE) ?
                    "ON_TARGET" : "TRACKING";

            Log.d(TAG, String.format(Locale.US,
                    "[PARAM1_ALIGNMENT] SEND_PAN:%d SEND_TILT:%d | MEMORY:(%.2f,%.2f) | ERROR:(%.2f,%.2f) | ACC:%.1f%% | STATUS:%s | STABLE:%d",
                    pan, tilt, memoryHip[0], memoryHip[1], errorX, errorY, accuracy, status, stableCount
            ));

            Log.d(TAG, "Send to ESP32 via UDP: MOVE:" + pan + "," + tilt);
        }
    }

    private void freezeServo() {
        if (udpManager != null && isUdpConnected) {
            udpManager.sendRawCommand("FREEZE");
            Log.d(TAG, "FREEZE command sent via UDP");
        }
    }

    private void unfreezeServo() {
        if (udpManager != null && isUdpConnected) {
            udpManager.sendRawCommand("UNFREEZE");
            Log.d(TAG, "UNFREEZE command sent via UDP");
        }
    }

    private void sendCenter() {
        if (udpManager != null && isUdpConnected) {
            udpManager.sendRawCommand("CENTER");
            lastSentPan = 90;
            lastSentTilt = 90;
            lastSendTime = 0;
            Log.d(TAG, "CENTER command sent via UDP");
        }
    }

    // ==================== MEDIAPIPE POSE DETECTION ====================
    /**
     * Inisialisasi MediaPipe Pose Landmarker untuk deteksi pose tubuh
     * - Menggunakan model "pose_landmarker_lite.task" (ringan untuk mobile)
     * - Mode LIVE_STREAM untuk pemrosesan real-time per frame
     * - Result listener mengarah ke processPoseResult()
     * - Jika inisialisasi gagal → Toast error dan finish activity
     * - Dipanggil dari onCreate()
     */
    private void initPoseLandmarker() {
        try {
            PoseLandmarker.PoseLandmarkerOptions options =
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(BaseOptions.builder()
                                    .setModelAssetPath("pose_landmarker_lite.task")
                                    .build())
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setResultListener(this::processPoseResult)
                            .build();
            poseLandmarker = PoseLandmarker.createFromOptions(this, options);
            Log.d(TAG, "MediaPipe Pose initialized");
        } catch (Exception e) {
            Log.e(TAG, "Init failed: " + e.getMessage());
            Toast.makeText(this, "Gagal inisialisasi MediaPipe", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private PointF[] extractKeyPoints(List<NormalizedLandmark> landmarks, int viewWidth, int viewHeight) {
        if (landmarks == null || landmarks.size() < 29) return null;
        if (viewWidth == 0 || viewHeight == 0) return null;

        float MIN_VISIBILITY = 0.7f;
        float MIN_PRESENCE = 0.6f;
        int[] indices = {RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE};
        PointF[] points = new PointF[6];

        for (int i = 0; i < indices.length; i++) {
            NormalizedLandmark lm = landmarks.get(indices[i]);
            if (lm == null) { points[i] = null; continue; }
            float visibility = lm.visibility().orElse(0f);
            float presence = lm.presence().orElse(0f);
            if (visibility < MIN_VISIBILITY || presence < MIN_PRESENCE) {
                points[i] = null;
                continue;
            }
            float x = (1.0f - lm.x()) * viewWidth;
            x = viewWidth - x;
            float y = lm.y() * viewHeight;
            y = viewHeight - y;
            points[i] = new PointF(x, y);
        }
        return points;
    }

    private float calculateBoundingBoxHeight(PointF[] points) {
        if (points == null) return 0f;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        for (PointF p : points) {
            if (p != null) {
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
        }
        if (minY != Float.MAX_VALUE && maxY != Float.MIN_VALUE) {
            return (maxY - minY) / overlayView.getHeight();
        }
        return 0f;
    }

    /**
     * Callback dari MediaPipe Pose (dipanggil setiap frame)
     * - Menerima hasil deteksi 33 titik tubuh
     * - Ekstrak 6 titik kanan: shoulder, elbow, wrist, hip, knee, ankle
     * - Hitung posisi center tubuh (dari hip)
     * - Update overlay (skeleton, titik merah, progress bar)
     * - BEDAKAN MODE:
     *   - CENTERING: proses centering (processCentering)
     *   - WORKOUT: hitung sudut & klasifikasi pose
     * - Juga deteksi gestur tangan horizontal untuk trigger centering
     */
    private void processPoseResult(PoseLandmarkerResult result, Object inputImage) {
        // ========== HITUNG FPS ==========
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) { // Update setiap 1 detik
            currentFps = (float) frameCount / ((now - lastFpsTime) / 1000f);
            Log.d(TAG, String.format(Locale.US, "📊 [FPS] %.1f fps", currentFps));
            frameCount = 0;
            lastFpsTime = now;
        }

        // ========== KODE EXISTING ==========
        isProcessing = false;
        if (result == null || result.landmarks().isEmpty()) {
            framesWithoutDetection++;
            runOnUiThread(() -> {
                tvStatus.setText("❌ Tidak ada pose");
                overlayView.clearPoints();
            });
            return;
        }

        framesWithoutDetection = 0;

        try {
            List<NormalizedLandmark> landmarks = result.landmarks().get(0);
            int viewWidth = overlayView.getWidth();
            int viewHeight = overlayView.getHeight();
            PointF[] keyPoints = extractKeyPoints(landmarks, viewWidth, viewHeight);

            if (keyPoints != null && keyPoints[3] != null) {
                float centerXPixel = keyPoints[3].x;
                float centerYPixel = keyPoints[3].y;
                float rawCenterX = centerXPixel / viewWidth;
                float rawCenterY = centerYPixel / viewHeight;
                currentBoxHeight = calculateBoundingBoxHeight(keyPoints);

                rawCenterX = Math.max(0.10f, Math.min(0.90f, rawCenterX));
                rawCenterY = Math.max(0.10f, Math.min(0.90f, rawCenterY));

                smoothedCenterX = rawCenterX;
                smoothedCenterY = rawCenterY;

                smoothedCenterX = Math.max(0.08f, Math.min(0.92f, smoothedCenterX));
                smoothedCenterY = Math.max(0.08f, Math.min(0.92f, smoothedCenterY));

                runOnUiThread(() -> {
                    tvStatus.setText("✅ Pose Terdeteksi");
                    overlayView.setPoints(keyPoints);
                    overlayView.setCenter(smoothedCenterX, smoothedCenterY);
                    overlayView.setStableCount(stableCount, STABLE_FRAMES_REQUIRED);
                    updateZoneInfo();

                    // TAMBAHKAN INI SETELAH updateZoneInfo()
                    if (detectRightArmHorizontal(keyPoints) &&
                            (now - lastGestureTime) > GESTURE_COOLDOWN_MS) {
                        lastGestureTime = now;

                        if (tvCorrection != null) {
                            tvCorrection.setText("Gestur terdeteksi! Centering...");
                            tvCorrection.setTextColor(Color.parseColor("#4CAF50"));
                            new Handler().postDelayed(() -> {
                                if (tvCorrection != null && tvCorrection.getText().equals("Gestur terdeteksi! Centering...")) {
                                    tvCorrection.setText("");
                                }
                            }, 2000);
                        }

                        triggerCentering();
                    }


                    if (currentState == AppState.CENTERING) {
                        checkDistanceAndSpeak();
                        updateCorrectionText();
                        if (isCenteringActive && !isPositionCentered && !isFrozen && !isWaitingPeriod) {
                            processCentering();
                        }
                    } else if (currentState == AppState.WORKOUT && isWorkoutActive) {
                        calculateJointAngles(keyPoints);
                        classifyPose();
                        checkFormCorrection();
                        tvStatus.setText(String.format("Siku: %.1f° | Lutut: %.1f° | Torso: %.1f°",
                                smoothedElbowAngle, smoothedKneeAngle, smoothedTorsoAngle));
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Pose error: " + e.getMessage());
        }
    }

    // ==================== CENTERING METHODS ====================
    private void updateZoneInfo() {
        String zone = getZoneString(smoothedCenterX, smoothedCenterY);
        if (tvZoneInfo != null) tvZoneInfo.setText(String.format("Posisi: %s", zone));
    }

    private String getZoneString(float x, float y) {
        if (x < 0.33f) {
            return y < 0.33f ? "Kiri Atas" : (y > 0.66f ? "Kiri Bawah" : "Kiri");
        } else if (x > 0.66f) {
            return y < 0.33f ? "Kanan Atas" : (y > 0.66f ? "Kanan Bawah" : "Kanan");
        } else {
            if (y < 0.33f) return "Atas";
            if (y > 0.66f) return "Bawah";
            return "TENGAH";
        }
    }

    /**
     * UI koreksi: pakai DEAD_ZONE (3%) bukan POSITION_TOLERANCE (8%)
     */
    private void updateCorrectionText() {
        float offX = Math.abs(smoothedCenterX - 0.5f);
        float offY = Math.abs(smoothedCenterY - 0.5f);

        if (offX <= CENTER_TOLERANCE && offY <= CENTER_TOLERANCE) {
            if (tvCorrection != null) {
                tvCorrection.setText(String.format("✅ Menuju tengah: %d/%d", stableCount, STABLE_FRAMES_REQUIRED));
                tvCorrection.setTextColor(Color.parseColor("#4CAF50"));
            }
        } else {
            if (tvCorrection != null) {
                tvCorrection.setText("🔄 Servo menyesuaikan...");
                tvCorrection.setTextColor(Color.YELLOW);
            }
        }
    }

    private void checkDistanceAndSpeak() {
        if (currentBoxHeight < MIN_IDEAL_BOX_SIZE) {
            speakDistance("Mendekatlah ke kamera");
            if (tvCorrection != null) {
                tvCorrection.setText("TERLALU JAUH → Mendekat");
                tvCorrection.setTextColor(Color.RED);
            }
        } else if (currentBoxHeight > MAX_IDEAL_BOX_SIZE) {
            speakDistance("Menjauhlah dari kamera");
            if (tvCorrection != null) {
                tvCorrection.setText("TERLALU DEKAT → Menjauh");
                tvCorrection.setTextColor(Color.RED);
            }
        } else if (currentBoxHeight < TARGET_BOX_SIZE - 0.05f && currentBoxHeight > 0) {
            if (tvCorrection != null) {
                tvCorrection.setText("Mendekatlah sedikit lagi");
                tvCorrection.setTextColor(Color.YELLOW);
            }
        } else if (currentBoxHeight > TARGET_BOX_SIZE + 0.05f && currentBoxHeight > 0) {
            if (tvCorrection != null) {
                tvCorrection.setText("Menjauhlah sedikit lagi");
                tvCorrection.setTextColor(Color.YELLOW);
            }
        } else if (currentBoxHeight > 0) {
            if (tvCorrection != null && !tvCorrection.getText().toString().contains("Servo")) {
                tvCorrection.setText("Jarak ideal");
                tvCorrection.setTextColor(Color.GREEN);
            }
        }
    }

    /**
     * Deteksi tangan kanan lurus horizontal ke samping
     * - Cek posisi shoulder, elbow, wrist
     * - Syarat: deltaX > 20, deltaY < 120, elbow lurus
     * - Cooldown 3 detik (GESTURE_COOLDOWN_MS)
     * - Jika terdeteksi → panggil triggerCentering()
     * - Dipanggil dari processPoseResult() setiap frame
     */
    private boolean detectRightArmHorizontal(PointF[] keyPoints) {
        // VALIDASI 1: Cek keyPoints
        if (keyPoints == null || keyPoints.length < 3) {
            return false;
        }

        PointF shoulder = keyPoints[0];
        PointF elbow = keyPoints[1];
        PointF wrist = keyPoints[2];

        // VALIDASI 2: Cek landmark tidak null
        if (shoulder == null || elbow == null || wrist == null) {
            Log.d(TAG, "🔍 [GESTUR] Landmark null - shoulder:" + (shoulder!=null) +
                    ", elbow:" + (elbow!=null) + ", wrist:" + (wrist!=null));
            return false;
        }

        float deltaX = wrist.x - shoulder.x;
        float deltaY = wrist.y - shoulder.y;

        float armLength = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float elbowAngle = calculateAngle(shoulder, elbow, wrist);

        boolean isToRight = deltaX > 120;
        boolean isHorizontal = Math.abs(deltaY) < 45;
        boolean isElbowStraight = elbowAngle > 155f;
        boolean isArmLongEnough = armLength > MIN_ARM_LENGTH;

        boolean result = isToRight
                && isHorizontal
                && isElbowStraight
                && isArmLongEnough;

        // LOG SATU: Tampilkan nilai delta + hasil
        Log.d(TAG, "🔍 [GESTUR] deltaX=" + deltaX
                + ", deltaY=" + deltaY
                + ", elbowAngle=" + elbowAngle
                + ", armLength=" + armLength
                + " → RESULT=" + result);

        return result;
    }

    /**
     * Servo mengikuti user
     * - Terus kirim pan/tilt setiap frame
     * - Jika HIP masuk kotak → LANGSUNG ke WORKOUT
     */
    private void processCentering() {
        if (isWaitingPeriod || isFrozen || isPositionCentered) return;
        if (!isCenteringActive) return;

        float rawX = smoothedCenterX;
        float rawY = smoothedCenterY;

        memoryHip[0] = rawX;
        memoryHip[1] = rawY;
        hasMemory = true;

        float errorX = rawX - 0.5f;
        float errorY = rawY - 0.5f;
        float offX = Math.abs(errorX);
        float offY = Math.abs(errorY);

        if (offX <= CENTER_TOLERANCE && offY <= CENTER_TOLERANCE) {
            stableCount++;
            Log.d(TAG, String.format(Locale.US,
                    "[CENTER_TRACKING] HIP_IN_BOX stable:%d/%d | HIP:(%.3f,%.3f)",
                    stableCount, STABLE_FRAMES_REQUIRED, rawX, rawY
            ));

            if (stableCount >= STABLE_FRAMES_REQUIRED && !isPositionCentered) {
                Log.d(TAG, " [CENTER] HIP stabil di kotak! Lanjut ke tahap berikutnya.");
                isPositionCentered = true;
                onPositionCentered();
                return;
            }
            return;
        }

        stableCount = 0;

        int pan = lastSentPan + calculateTrackingStep(errorX);
        int tilt = lastSentTilt + calculateTrackingStep(errorY);
        pan = Math.max(PAN_MIN, Math.min(PAN_MAX, pan));
        tilt = Math.max(TILT_MIN, Math.min(TILT_MAX, tilt));

        sendPanTilt(pan, tilt);
    }

    private int calculateTrackingStep(float error) {
        int step = Math.round(error * TRACKING_GAIN);
        if (step == 0 && Math.abs(error) > CENTER_TOLERANCE) {
            step = error > 0 ? 1 : -1;
        }
        return Math.max(-MAX_TRACKING_STEP, Math.min(MAX_TRACKING_STEP, step));
    }

    /**
     * Dipanggil saat posisi user stabil di kotak tengah
     * - FREEZE servo
     * - BEDAKAN MODE:
     *   - Centering awal → hold 2 detik → pindah ke workout
     *   - Re-centering → LANGSUNG resume workout (tanpa hold)
     * - Flag isReCenteringMode membedakan kedua mode
     */
    private void onPositionCentered() {
        isFrozen = true;
        freezeServo();

        if (isReCenteringMode) {
            // ========== RE-CENTERING SELESAI ==========
            //  TAMBAHKAN HOLD 2 DETIK SEPERTI CENTERING AWAL
            speak("Pertahankan posisimu");
            tvInstruction.setText("PERTAHANKAN POSISI!");
            tvCountdown.setVisibility(View.GONE);

            new CountDownTimer(HOLD_COUNTDOWN_SECONDS * 1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                }
                @Override
                public void onFinish() {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    tvCountdown.setVisibility(View.GONE);
                    Log.d(TAG, "Re-centering hold complete, resuming WORKOUT");

                    isReCenteringMode = false;
                    currentState = AppState.WORKOUT;
                    isWorkoutActive = true;
                    isCenteringActive = false;

                    resumeWorkout();
                    updateUIForState();
                    tvInstruction.setText("");
                    tvCountdown.setVisibility(View.GONE);
                }
            }.start();

        } else {
            // ========== CENTERING AWAL ==========
            speak("Pertahankan posisimu");
            tvInstruction.setText("PERTAHANKAN POSISI!");
            tvCountdown.setVisibility(View.GONE);

            new CountDownTimer(HOLD_COUNTDOWN_SECONDS * 1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                }
                @Override
                public void onFinish() {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    tvCountdown.setVisibility(View.GONE);
                    Log.d(TAG, "Centering success, switching to WORKOUT");
                    transitionToWorkout();
                }
            }.start();
        }
    }

    /**
     * Transisi dari centering ke workout
     * - currentState = WORKOUT
     * - Update UI (tombol stop muncul, tombol center hilang)
     * - Panggil startWorkout()
     * - Dipanggil dari onPositionCentered() (mode centering awal)
     */
    private void transitionToWorkout() {
        currentState = AppState.WORKOUT;
        updateUIForState();
        startWorkout();
    }

    private void updateUIForState() {
        if (currentState == AppState.CENTERING) {
            btnOneTapCenter.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.GONE);
            tvWorkoutType.setVisibility(View.VISIBLE);
            tvRepsCount.setVisibility(View.GONE);
            tvPosition.setVisibility(View.GONE);
            tvTimer.setVisibility(View.GONE);
            tvInstruction.setVisibility(View.VISIBLE);
            tvCorrection.setVisibility(View.VISIBLE);
            tvZoneInfo.setVisibility(View.VISIBLE);
            tvCountdown.setVisibility(View.VISIBLE);
            tvWorkoutType.setText(workoutType.toUpperCase());
        } else {
            btnOneTapCenter.setVisibility(View.GONE);
            btnStop.setVisibility(View.VISIBLE);
            tvWorkoutType.setVisibility(View.VISIBLE);
            tvRepsCount.setVisibility(View.VISIBLE);
            tvPosition.setVisibility(View.VISIBLE);
            tvTimer.setVisibility(View.VISIBLE);
            tvInstruction.setVisibility(View.GONE);
            tvCorrection.setVisibility(View.GONE);
            tvZoneInfo.setVisibility(View.GONE);
            tvCountdown.setVisibility(View.GONE);
            tvWorkoutType.setText(workoutType.toUpperCase());
            tvRepsCount.setText(String.valueOf(totalReps));
            tvPosition.setText("UP");
            tvTimer.setText("00:00");
        }
    }
    /**
     * Memulai proses centering awal (sebelum workout)
     * - Servo ke posisi 90,90 lalu FREEZE
     * - Countdown 5 detik persiapan
     * - Setelah countdown, mulai tracking ke kotak tengah
     * - Panggil dari: tombol "One Tap Center" atau gestur tangan horizontal
     */
    private void startCenteringProcess() {
        if (isCenteringActive) resetCentering();
        if (!isUdpConnected) {
            Toast.makeText(this, "ESP32 tidak terhubung!", Toast.LENGTH_LONG).show();
            return;
        }

        sendCenter();
        sendPanTilt(90, 90, true);
        freezeServo();

        isCenteringActive = false;
        isPositionCentered = false;
        isFrozen = true;
        stableCount = 0;
        hasMemory = false;
        hasSmoothedData = false;
        smoothedCenterX = 0.5f;
        smoothedCenterY = 0.5f;
        isWaitingPeriod = false;

        tvInstruction.setText("PERSIAPAN CENTERING...");
        tvCountdown.setVisibility(View.VISIBLE);

        new CountDownTimer(PREPARE_COUNTDOWN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(String.valueOf(secondsLeft + 1));
                if (isTtsReady && tts != null) {
                    tts.speak(String.valueOf(secondsLeft + 1), TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
            @Override
            public void onFinish() {
                tvCountdown.setVisibility(View.GONE);
                endWaitingPeriod();
            }
        }.start();
    }

    /**
     * Mengakhiri masa tunggu dan memulai centering aktif
     * - UNFREEZE servo
     * - Kirim MOVE ke posisi awal berdasarkan posisi user
     * - isCenteringActive = true (mulai tracking)
     * - Dipanggil setelah countdown 5 detik selesai
     */
    private void endWaitingPeriod() {
        isWaitingPeriod = false;
        isCenteringActive = true;
        isFrozen = false;

        unfreezeServo();
        Log.d(TAG, "UNFREEZE sent");

        centeringHandler.postDelayed(() -> {
            tvCountdown.setVisibility(View.GONE);
            tvInstruction.setText("MENYELARASKAN POSISI...");
            tvStatus.setText("Memulai penyelarasan");
            Log.d(TAG, "========== CENTERING ACTIVE ==========");
            Log.d(TAG, "Tracking loop will adjust servo from current position");
        }, 2000);
    }

    private void resetCentering() {
        isCenteringActive = false;
        isPositionCentered = false;
        isFrozen = true;
        isWaitingPeriod = false;
        stableCount = 0;
        hasMemory = false;
        hasSmoothedData = false;
        framesWithoutDetection = 0;
        smoothedCenterX = 0.5f;
        smoothedCenterY = 0.5f;
        tvInstruction.setText("Berdiri di depan kamera");
        tvCountdown.setVisibility(View.GONE);
        if (isUdpConnected) {
            sendCenter();
            freezeServo();
        }
    }

    /**
     * Trigger centering dari tombol atau gestur
     * - BEDAKAN MODE:
     *   - Jika sedang WORKOUT → Re-centering (startReCentering)
     *   - Jika belum WORKOUT → Centering awal (startCenteringProcess)
     * - Dipanggil dari tombol "One Tap Center" atau deteksi gestur
     */
    private void triggerCentering() {
        if (!isUdpConnected) {
            Toast.makeText(this, "ESP32 tidak terhubung!", Toast.LENGTH_LONG).show();
            return;
        }

        if (currentState == AppState.WORKOUT && isWorkoutActive) {
            speak("Menyesuaikan ulang posisi");
            startReCentering();
        } else if (currentState == AppState.CENTERING && !isCenteringActive && !isPositionCentered) {
            speak("Memulai centering");
            startCenteringProcess();
        }
    }

    /**
     * Memulai re-centering saat user pindah posisi di tengah workout
     * - PAUSE workout (timer pause)
     * - FREEZE servo (TANPA kembali ke 90,90)
     * - Countdown 5 detik (sama seperti centering awal)
     * - Setelah countdown → startReCenteringProcess()
     * - Dipanggil dari triggerCentering() saat currentState = WORKOUT
     */
    private void startReCentering() {
        if (!isUdpConnected) return;

        // ========== PAUSE WORKOUT ==========
        isWorkoutActive = false;
        currentState = AppState.CENTERING;

        //  SIMPAN ELAPSED TIME SEBELUM DI-RESET
        pausedElapsedTime = elapsedTime;  // <-- TAMBAHKAN INI
        Log.d(TAG, "⏸️ PAUSED at elapsedTime: " + pausedElapsedTime + "ms");

        if (workoutTimer != null) {
            workoutTimer.cancel();
            workoutTimer = null;
        }

        // Reset state centering
        isCenteringActive = false;
        isPositionCentered = false;
        isFrozen = true;
        stableCount = 0;
        hasMemory = false;
        isWaitingPeriod = false;
        isReCenteringMode = true;
        isProcessing = false;

        freezeServo();

        tvInstruction.setText("PERSIAPAN RE-CENTERING...");
        tvCountdown.setVisibility(View.VISIBLE);

        new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isFinishing() || isDestroyed()) {
                    cancel();
                    return;
                }
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(String.valueOf(secondsLeft + 1));
                if (isTtsReady && tts != null) {
                    tts.speak(String.valueOf(secondsLeft + 1), TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
            @Override
            public void onFinish() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                tvCountdown.setVisibility(View.GONE);
                startReCenteringProcess();
            }
        }.start();
    }

    /**
     * Proses re-centering setelah countdown selesai
     * - UNFREEZE servo (dari posisi terakhir)
     * - Kirim MOVE ke posisi baru berdasarkan posisi user
     * - Proses centering tracking ke kotak (sama seperti awal)
     * - Dipanggil setelah countdown 5 detik di startReCentering()
     */
    private void startReCenteringProcess() {
        // Reset state
        isCenteringActive = false;
        isPositionCentered = false;
        isFrozen = false;
        stableCount = 0;
        hasMemory = false;
        isWaitingPeriod = false;

        // Baca posisi awal user
        isWaitingPeriod = true;
        tvInstruction.setText("MEMBACA POSISI...");

        centeringHandler.postDelayed(() -> {
            isWaitingPeriod = false;
            isCenteringActive = true;
            isFrozen = false;
            unfreezeServo();

            centeringHandler.postDelayed(() -> {
                tvInstruction.setText("MENYELARASKAN POSISI...");
                tvStatus.setText("Memulai penyelarasan");
                Log.d(TAG, "Re-centering tracking loop will adjust servo from current position");
            }, 1500);
        }, 1500);
    }

    /**
     * Resume workout setelah re-centering selesai
     * - isWorkoutActive = true
     * - Lanjutkan timer dari waktu terakhir (elapsedTime)
     * - Panggil startWorkoutTimer()
     * - Dipanggil dari onPositionCentered() saat isReCenteringMode = true
     */
    private void resumeWorkout() {
        isWorkoutActive = true;

        //  GUNAKAN PAUSED ELAPSED TIME, BUKAN elapsedTime
        startTime = System.currentTimeMillis() - pausedElapsedTime;
        Log.d(TAG, "▶️ RESUMING from elapsedTime: " + pausedElapsedTime + "ms");

        startWorkoutTimer();
    }

    // ==================== PERHITUNGAN SUDUT ====================
    /**
     * Menghitung sudut siku, lutut, dan torso dari 6 titik kanan
     * - Sudut siku: Shoulder - Elbow - Wrist
     * - Sudut lutut: Hip - Knee - Ankle
     * - Sudut torso: Shoulder - Hip (kemiringan tubuh)
     * - Gunakan smooth agar data lebih stabil (EMA)
     * - Dipanggil dari processPoseResult() saat WORKOUT
     */
    private void calculateJointAngles(PointF[] kp) {
        if (kp == null || kp.length < 6) return;

        float rawElbow = calculateAngle(kp[0], kp[1], kp[2]);
        float rawKnee = calculateAngle(kp[3], kp[4], kp[5]);
        float rawTorso = calculateTorsoAngle(kp[0], kp[3]);

        //  LOG KOORDINAT PIXEL BERDASARKAN JENIS LATIHAN
        if (workoutType.equalsIgnoreCase("PUSH_UP")) {
            // PUSH_UP: A(Bahu), B(Siku), C(Tangan)
            PointF A = kp[0]; // Shoulder
            PointF B = kp[1]; // Elbow
            PointF C = kp[2]; // Wrist

            if (A != null && B != null && C != null) {
                Log.d(TAG, String.format(Locale.US,
                        "📍 [COORD_PUSHUP] A(%.0f,%.0f) B(%.0f,%.0f) C(%.0f,%.0f) | Angle: %.1f°",
                        A.x, A.y, B.x, B.y, C.x, C.y, rawElbow
                ));
            }
        } else if (workoutType.equalsIgnoreCase("SQUAT")) {
            // SQUAT: A(Pinggul), B(Lutut), C(Kaki)
            PointF A = kp[3]; // Hip
            PointF B = kp[4]; // Knee
            PointF C = kp[5]; // Ankle

            if (A != null && B != null && C != null) {
                Log.d(TAG, String.format(Locale.US,
                        "📍 [COORD_SQUAT] A(%.0f,%.0f) B(%.0f,%.0f) C(%.0f,%.0f) | Angle: %.1f°",
                        A.x, A.y, B.x, B.y, C.x, C.y, rawKnee
                ));
            }
        }

        if (workoutType.equalsIgnoreCase("PUSH_UP")) {
            if (rawElbow < 10f || rawElbow > 170f) return;
            if (smoothedElbowAngle == 0f && rawElbow > 0) {
                smoothedElbowAngle = rawElbow;
                smoothedTorsoAngle = rawTorso;
                return;
            }
            smoothedElbowAngle = smoothedElbowAngle * 0.4f + rawElbow * 0.6f;
            smoothedTorsoAngle = smoothedTorsoAngle * 0.6f + rawTorso * 0.4f;
        }

        if (workoutType.equalsIgnoreCase("SQUAT")) {
            if (rawKnee < 10f || rawKnee > 170f) return;
            if (smoothedKneeAngle == 0f && rawKnee > 0) {
                smoothedKneeAngle = rawKnee;
                smoothedTorsoAngle = rawTorso;
                return;
            }
            smoothedKneeAngle = smoothedKneeAngle * 0.6f + rawKnee * 0.4f;
            smoothedTorsoAngle = smoothedTorsoAngle * 0.6f + rawTorso * 0.4f;
        }
    }

    private float calculateAngle(PointF a, PointF b, PointF c) {
        if (a == null || b == null || c == null) return 0f;
        float baX = a.x - b.x, baY = a.y - b.y;
        float bcX = c.x - b.x, bcY = c.y - b.y;
        float dot = baX * bcX + baY * bcY;
        float magBA = (float) Math.sqrt(baX * baX + baY * baY);
        float magBC = (float) Math.sqrt(bcX * bcX + bcY * bcY);
        if (magBA < 0.001f || magBC < 0.001f) return 0f;
        float cos = Math.max(-1.0f, Math.min(1.0f, dot / (magBA * magBC)));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    private float calculateTorsoAngle(PointF shoulder, PointF hip) {
        if (shoulder == null || hip == null) return 0f;
        float dx = Math.abs(hip.x - shoulder.x);
        float dy = Math.abs(hip.y - shoulder.y);
        return (float) Math.toDegrees(Math.atan2(dx, dy));
    }

    // ==================== RULE-BASED THRESHOLD & FSM ====================
    /**
     * Mengklasifikasikan pose berdasarkan threshold sudut
     * - Push-up: DOWN jika siku 30-120°, UP jika siku ≥ 130°
     * - Squat: DOWN jika lutut 80-110°, UP jika lutut ≥ 150°
     * - Juga cek form torso (tidak boleh terlalu miring)
     * - Jika transisi valid → panggil incrementRepCount()
     * - Dipanggil dari processPoseResult() saat WORKOUT
     */
    private void classifyPose() {
        float torsoThreshold = workoutType.equalsIgnoreCase("PUSH_UP") ?
                TORSO_TILT_THRESHOLD_PUSHUP : TORSO_TILT_THRESHOLD_SQUAT;
        boolean formValid = smoothedTorsoAngle <= torsoThreshold;

        String position = "UP";
        long now = System.currentTimeMillis();

        if (workoutType.equalsIgnoreCase("PUSH_UP")) {
            boolean inDownRange = (smoothedElbowAngle >= PUSHUP_DOWN_MIN &&
                    smoothedElbowAngle <= PUSHUP_DOWN_MAX);

            if (inDownRange && formValid) {
                consecutiveInRange++;
            } else {
                consecutiveInRange = 0;
            }

            boolean isStable = consecutiveInRange >= MIN_CONSECUTIVE;

            if (inDownRange && formValid) {
                position = "DOWN";
            } else if (smoothedElbowAngle >= PUSHUP_UP_THRESHOLD) {
                position = "UP";
            }

            if (now - lastStateChangeTime >= STATE_DEBOUNCE_MS) {
                if (isStable && workoutCurrentState == WorkoutState.UP) {
                    workoutCurrentState = WorkoutState.DOWN;
                    wasInDownState = true;
                    isValidRep = true;
                    lastStateChangeTime = now;
                    Log.d(TAG, "UP -> DOWN at " + smoothedElbowAngle + "°");
                } else if (!inDownRange && smoothedElbowAngle >= PUSHUP_UP_THRESHOLD &&
                        workoutCurrentState == WorkoutState.DOWN) {
                    workoutCurrentState = WorkoutState.UP;
                    lastStateChangeTime = now;
                    if (wasInDownState && isValidRep) {
                        incrementRepCount();
                        wasInDownState = false;
                        isValidRep = false;
                    }
                }
            }
        } else if (workoutType.equalsIgnoreCase("SQUAT")) {
            boolean inDownRange = (smoothedKneeAngle >= SQUAT_DOWN_MIN &&
                    smoothedKneeAngle <= SQUAT_DOWN_MAX && formValid);

            if (inDownRange) {
                position = "DOWN";
                consecutiveInRange++;
            } else {
                consecutiveInRange = 0;
            }

            boolean isStable = consecutiveInRange >= MIN_CONSECUTIVE;

            if (!inDownRange && smoothedKneeAngle >= SQUAT_UP_THRESHOLD) {
                position = "UP";
            }

            if (now - lastStateChangeTime >= STATE_DEBOUNCE_MS) {
                if (isStable && workoutCurrentState == WorkoutState.UP) {
                    workoutCurrentState = WorkoutState.DOWN;
                    wasInDownState = true;
                    isValidRep = true;
                    lastStateChangeTime = now;
                    Log.d(TAG, "SQUAT UP -> DOWN at " + smoothedKneeAngle + "°");
                } else if (!inDownRange && smoothedKneeAngle >= SQUAT_UP_THRESHOLD &&
                        workoutCurrentState == WorkoutState.DOWN) {
                    workoutCurrentState = WorkoutState.UP;
                    lastStateChangeTime = now;
                    if (wasInDownState && isValidRep) {
                        incrementRepCount();
                        wasInDownState = false;
                        isValidRep = false;
                    }
                }
            }
        }

        final String finalPosition = position;
        runOnUiThread(() -> tvPosition.setText(finalPosition));
    }

    private void checkFormCorrection() {
        long now = System.currentTimeMillis();
        String correction = getFormCorrectionMessage();

        //  PISAHKAN LOG UNTUK PUSH_UP DAN SQUAT
        String angleInfo;
        if (workoutType.equalsIgnoreCase("PUSH_UP")) {
            angleInfo = "ELBOW:" + String.format(Locale.US, "%.1f", smoothedElbowAngle);
        } else if (workoutType.equalsIgnoreCase("SQUAT")) {
            angleInfo = "KNEE:" + String.format(Locale.US, "%.1f", smoothedKneeAngle);
        } else {
            angleInfo = "UNKNOWN";
        }

        if (correction == null) {
            pendingCorrection = "";
            pendingCorrectionCount = 0;
            Log.d(TAG, String.format(Locale.US,
                    "[PARAM2_CORRECTION] CORRECTION:\"NONE\" | %s | TORSO:%.1f | STATE:%s | VALID:YES",
                    angleInfo, smoothedTorsoAngle, workoutCurrentState
            ));
            return;
        }

        if (correction.equals(pendingCorrection)) {
            pendingCorrectionCount++;
        } else {
            pendingCorrection = correction;
            pendingCorrectionCount = 1;
        }

        if (pendingCorrectionCount < CORRECTION_STABLE_FRAMES_REQUIRED) {
            Log.d(TAG, String.format(Locale.US,
                    "[PARAM2_CORRECTION] CANDIDATE:\"%s\" | %s | TORSO:%.1f | STATE:%s | STABLE:%d/%d",
                    correction, angleInfo, smoothedTorsoAngle, workoutCurrentState,
                    pendingCorrectionCount, CORRECTION_STABLE_FRAMES_REQUIRED
            ));
            return;
        }

        if (now - lastCorrectionTime < CORRECTION_COOLDOWN) return;
        if (correction.equals(lastCorrection) && (now - lastCorrectionTime < SAME_CORRECTION_COOLDOWN)) return;

        Log.d(TAG, String.format(Locale.US,
                "[PARAM2_CORRECTION] CORRECTION:\"%s\" | %s | TORSO:%.1f | STATE:%s | VALID:YES",
                correction, angleInfo, smoothedTorsoAngle, workoutCurrentState
        ));

        speak(correction);
        lastCorrectionTime = now;
        lastCorrection = correction;
    }

    private String getFormCorrectionMessage() {
        if (workoutType.equalsIgnoreCase("PUSH_UP")) {
            if (smoothedElbowAngle <= 0f) return null;
            if (smoothedElbowAngle < PUSHUP_TOO_DEEP) {
                return "Jangan terlalu dalam";
            }
            if (smoothedTorsoAngle > TORSO_TILT_THRESHOLD_PUSHUP) {
                return "Jaga badan tetap lurus";
            }
            if (smoothedElbowAngle > 125f && smoothedElbowAngle < PUSHUP_UP_THRESHOLD) {
                return "Turunkan dada lebih rendah";
            }
            return null;
        }

        if (workoutType.equalsIgnoreCase("SQUAT")) {
            if (smoothedKneeAngle <= 0f) return null;
            if (smoothedKneeAngle < SQUAT_TOO_DEEP) {
                return "Jangan terlalu dalam";
            }
            if (smoothedTorsoAngle > TORSO_TILT_THRESHOLD_SQUAT) {
                return "Tegakkan badan Anda";
            }
            if (smoothedKneeAngle > SQUAT_DOWN_MAX && smoothedKneeAngle < SQUAT_UP_THRESHOLD) {
                return "Jongkok lebih dalam";
            }
        }

        return null;
    }
    /**
     * Menambah jumlah repetisi
     * - Cek cooldown (800ms antar rep) agar tidak double count
     * - TTS: "1", "2", "3"... (kelipatan 5: "Semangat, 5 repetisi")
     * - Update UI tvRepsCount
     * - Dipanggil dari classifyPose() saat transisi UP→DOWN valid
     */
    private void incrementRepCount() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRepTime < 800) {
            // 🔥 TAMBAHKAN LOG INI (REPETISI TIDAK VALID - TERLALU CEPAT)
            float angle = workoutType.equalsIgnoreCase("PUSH_UP") ? smoothedElbowAngle : smoothedKneeAngle;
            String angleLabel = workoutType.equalsIgnoreCase("PUSH_UP") ? "ELBOW" : "KNEE";
            Log.d(TAG, String.format(Locale.US,
                    "[PARAM3_REPETITION] COUNT:%d | %s:%.1f | STATE:%s | VALID:NO | REASON:TOO_FAST",
                    totalReps, angleLabel, angle, workoutCurrentState
            ));
            return;
        }
        lastRepTime = currentTime;
        totalReps++;

        // 🔥 TAMBAHKAN LOG INI (REPETISI VALID)
        float angle = workoutType.equalsIgnoreCase("PUSH_UP") ? smoothedElbowAngle : smoothedKneeAngle;
        String angleLabel = workoutType.equalsIgnoreCase("PUSH_UP") ? "ELBOW" : "KNEE";
        Log.d(TAG, String.format(Locale.US,
                "[PARAM3_REPETITION] COUNT:%d | %s:%.1f | STATE:%s | VALID:YES",
                totalReps, angleLabel, angle, workoutCurrentState
        ));

        if (totalReps % 5 == 0) {
            speak("Semangat, " + totalReps + " repetisi");
        } else {
            speak(String.valueOf(totalReps));
        }
        runOnUiThread(() -> tvRepsCount.setText(String.valueOf(totalReps)));
    }

    /**
     * Memulai sesi workout
     * - isWorkoutActive = true
     * - Timer mulai berjalan
     * - TTS "PUSH_UP/SQUAT dimulai..."
     * - Dipanggil dari transitionToWorkout() (setelah centering awal)
     */
    private void startWorkout() {
        isWorkoutActive = true;
        startTime = System.currentTimeMillis();

        new Handler().postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && isTtsReady && tts != null) {
                speak(workoutType.toUpperCase() + " dimulai. Pertahankan postur tubuh.");
            }
        }, 500);

        workoutTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isFinishing() || isDestroyed()) return;
                elapsedTime = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsedTime / 1000);
                tvTimer.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
            }
            @Override
            public void onFinish() {}
        }.start();
    }

    /**
     * Memulai ulang timer workout (untuk resume setelah re-centering)
     * - Digunakan saat re-centering selesai untuk melanjutkan timer, Timer lanjut dari waktu terakhir (elapsedTime)
     * - Dipanggil dari resumeWorkout()
     */
    private void startWorkoutTimer() {
        if (workoutTimer != null) {
            workoutTimer.cancel();
            workoutTimer = null;
        }

        workoutTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isFinishing() || isDestroyed()) return;
                if (isWorkoutActive) {
                    elapsedTime = System.currentTimeMillis() - startTime;
                    int seconds = (int) (elapsedTime / 1000);
                    tvTimer.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
                }
            }
            @Override
            public void onFinish() {}
        }.start();
    }

    /**
     * Menghentikan workout dan pindah ke save_screen
     * - Hentikan semua timer & handler
     * - TTS "Latihan selesai..."
     * - Kirim data workout ke save_screen
     * - Delay 150ms sebelum pindah activity
     */
    private void stopWorkout() {
        if (!isWorkoutActive) return;

        // 1. Tandai tidak aktif
        isWorkoutActive = false;

        // 2. Hentikan timer
        if (workoutTimer != null) {
            workoutTimer.cancel();
            workoutTimer = null;
        }

        // 3. Hentikan handler
        centeringHandler.removeCallbacksAndMessages(null);

        // 4. Matikan flag centering
        isCenteringActive = false;

        // 5. Hentikan processing
        isProcessing = false;

        // 6. TTS (opsional, biarkan selesai)
        if (isTtsReady && tts != null) {
            speak("Latihan selesai. " + totalReps + " repetisi. Bagus sekali!");
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // 7. Simpan data ke variable FINAL (biar aman)
        final String workoutTypeFinal = workoutType;
        final int totalRepsFinal = totalReps;
        final long durationFinal = durationMs;

        // 8. Pindah ke save_screen dengan delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;

            try {
                Intent intent = new Intent(centering_workout_screen.this, save_screen.class);
                intent.putExtra("WORKOUT_TYPE", workoutTypeFinal.toUpperCase());
                intent.putExtra("TOTAL_REPS", totalRepsFinal);
                intent.putExtra("DURATION", durationFinal);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error starting save_screen: " + e.getMessage());
                Intent intent = new Intent(centering_workout_screen.this, home_screen.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            finish();
        }, 150); // Delay 150ms
    }

    // ==================== KAMERA ====================
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Kamera diperlukan", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
                Log.d(TAG, "CAMERA STARTED");
            } catch (Exception e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        // ========== HITUNG FPS ==========
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            currentFps = (float) frameCount / ((now - lastFpsTime) / 1000f);
            Log.d(TAG, String.format(Locale.US, "📊 [FPS] %.1f fps", currentFps));
            frameCount = 0;
            lastFpsTime = now;
        }

        // ========== KODE EXISTING ==========
        frameCounter++;
        if (frameCounter % FRAME_SKIP != 0) {
            imageProxy.close();
            return;
        }

        if (isProcessing || poseLandmarker == null) {
            imageProxy.close();
            return;
        }
        isProcessing = true;

        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap != null) {
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, 854, 480, true);
                MPImage mpImage = new BitmapImageBuilder(resized).build();
                poseLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis());
                resized.recycle();
                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Analyze error: " + e.getMessage());
        } finally {
            imageProxy.close();
            isProcessing = false;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        byte[] yData = new byte[yBuffer.remaining()];
        byte[] uData = new byte[uBuffer.remaining()];
        byte[] vData = new byte[vBuffer.remaining()];
        yBuffer.get(yData);
        uBuffer.get(uData);
        vBuffer.get(vData);

        int w = imageProxy.getWidth();
        int h = imageProxy.getHeight();
        int yRowStride = image.getPlanes()[0].getRowStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int yOffset = y * yRowStride;
            int uvOffset = (y / 2) * uvRowStride;
            for (int x = 0; x < w; x++) {
                int Y = yData[yOffset + x] & 0xFF;
                int U = uData[uvOffset + (x / 2) * uvPixelStride] & 0xFF;
                int V = vData[uvOffset + (x / 2) * uvPixelStride] & 0xFF;

                int R = Math.max(0, Math.min(255, Y + (int) (1.402f * (V - 128))));
                int G = Math.max(0, Math.min(255, Y - (int) (0.344f * (U - 128) + 0.714f * (V - 128))));
                int B = Math.max(0, Math.min(255, Y + (int) (1.772f * (U - 128))));

                pixels[y * w + x] = (0xFF << 24) | (R << 16) | (G << 8) | B;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // ========== HENTIKAN SEMUA TIMER & HANDLER ==========
        if (workoutTimer != null) {
            workoutTimer.cancel();
            workoutTimer = null;
        }
        centeringHandler.removeCallbacksAndMessages(null);

        // ========== RESET SEMUA FLAG ==========
        isWorkoutActive = false;
        isCenteringActive = false;
        isPositionCentered = false;
        isFrozen = true;
        isWaitingPeriod = false;
        isReCenteringMode = false;
        isProcessing = false;
        currentState = AppState.CENTERING;  // ← RESET KE STATE AWAL

        // ========== TUTUP RESOURCE ==========
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (udpManager != null) {
            udpManager.setConnectionListener(null);
            // JANGAN panggil udpManager.close() karena Singleton!
        }

        Log.d(TAG, "onDestroy: All resources cleaned up");
    }

    // ==================== INNER CLASS OVERLAY ====================
    private class OverlayView extends View {
        private PointF[] points = null;
        private float centerX = 0.5f, centerY = 0.5f;
        private int stableCount = 0, requiredStable = 0;
        private Paint skeletonPaint, pointPaintBlue, pointPaintGreen, textPaint;
        private Paint zonePaint, centerPaint, linePaint, deadZonePaint;

        public OverlayView(Context context) {
            super(context);
            initPaints();
        }

        private void initPaints() {
            skeletonPaint = new Paint();
            skeletonPaint.setColor(Color.GREEN);
            skeletonPaint.setStrokeWidth(6f);
            skeletonPaint.setStyle(Paint.Style.STROKE);
            skeletonPaint.setAntiAlias(true);

            pointPaintBlue = new Paint();
            pointPaintBlue.setColor(Color.parseColor("#2196F3"));
            pointPaintBlue.setStyle(Paint.Style.FILL);
            pointPaintBlue.setAntiAlias(true);

            pointPaintGreen = new Paint();
            pointPaintGreen.setColor(Color.GREEN);
            pointPaintGreen.setStyle(Paint.Style.FILL);
            pointPaintGreen.setAntiAlias(true);

            textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(24f);
            textPaint.setAntiAlias(true);
            textPaint.setShadowLayer(2, 1, 1, Color.BLACK);

            zonePaint = new Paint();
            zonePaint.setColor(Color.argb(40, 255, 255, 255));

            centerPaint = new Paint();
            centerPaint.setColor(Color.parseColor("#FFD700"));
            centerPaint.setStrokeWidth(5f);
            centerPaint.setAntiAlias(true);

            linePaint = new Paint();
            linePaint.setColor(Color.parseColor("#FFAA00"));
            linePaint.setStrokeWidth(2f);
            linePaint.setStyle(Paint.Style.STROKE);

            deadZonePaint = new Paint();
            deadZonePaint.setColor(Color.argb(80, 0, 255, 0));
            deadZonePaint.setStyle(Paint.Style.FILL);
        }

        public void setPoints(PointF[] pts) { this.points = pts; invalidate(); }
        public void setCenter(float x, float y) { this.centerX = x; this.centerY = y; }
        public void setStableCount(int count, int required) { this.stableCount = count; this.requiredStable = required; }
        public void clearPoints() { this.points = null; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;

            float targetLeft = w * (0.5f - POSITION_TOLERANCE);
            float targetTop = h * (0.5f - POSITION_TOLERANCE);
            float targetRight = w * (0.5f + POSITION_TOLERANCE);
            float targetBottom = h * (0.5f + POSITION_TOLERANCE);
            canvas.drawRect(targetLeft, targetTop, targetRight, targetBottom, zonePaint);

            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.parseColor("#33FF33"));
            borderPaint.setStrokeWidth(3f);
            borderPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(targetLeft, targetTop, targetRight, targetBottom, borderPaint);

            float cx = w / 2f, cy = h / 2f;
            canvas.drawLine(cx - 60, cy, cx - 20, cy, centerPaint);
            canvas.drawLine(cx + 20, cy, cx + 60, cy, centerPaint);
            canvas.drawLine(cx, cy - 60, cx, cy - 20, centerPaint);
            canvas.drawLine(cx, cy + 20, cx, cy + 60, centerPaint);
            canvas.drawCircle(cx, cy, 15f, centerPaint);
            canvas.drawCircle(cx, cy, DEAD_ZONE_PX, deadZonePaint);

            if (points == null || points.length < 6) return;

            if (points[0] != null && points[1] != null)
                canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, skeletonPaint);
            if (points[1] != null && points[2] != null)
                canvas.drawLine(points[1].x, points[1].y, points[2].x, points[2].y, skeletonPaint);
            if (points[0] != null && points[3] != null)
                canvas.drawLine(points[0].x, points[0].y, points[3].x, points[3].y, skeletonPaint);
            if (points[3] != null && points[4] != null)
                canvas.drawLine(points[3].x, points[3].y, points[4].x, points[4].y, skeletonPaint);
            if (points[4] != null && points[5] != null)
                canvas.drawLine(points[4].x, points[4].y, points[5].x, points[5].y, skeletonPaint);

            String[] labels = {"Shoulder", "Elbow", "Wrist", "Hip", "Knee", "Ankle"};
            boolean[] isBlue = {true, false, false, true, false, false};

            for (int i = 0; i < points.length; i++) {
                if (points[i] != null) {
                    Paint paint = isBlue[i] ? pointPaintBlue : pointPaintGreen;
                    canvas.drawCircle(points[i].x, points[i].y, 16f, paint);
                    Paint stroke = new Paint();
                    stroke.setColor(Color.WHITE);
                    stroke.setStrokeWidth(2f);
                    stroke.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(points[i].x, points[i].y, 16f, stroke);
                    canvas.drawText(labels[i], points[i].x + 15, points[i].y - 10, textPaint);
                }
            }

            float px = centerX * w, py = centerY * h;
            Paint personCenterPaint = new Paint();
            personCenterPaint.setColor(Color.parseColor("#FF4444"));
            personCenterPaint.setStrokeWidth(3f);
            canvas.drawCircle(px, py, 20f, personCenterPaint);
            canvas.drawLine(px - 15, py, px + 15, py, personCenterPaint);
            canvas.drawLine(px, py - 15, px, py + 15, personCenterPaint);
            canvas.drawLine(px, py, cx, cy, linePaint);

            if (stableCount > 0 && requiredStable > 0) {
                float progress = (float) stableCount / requiredStable;
                Paint progressPaint = new Paint();
                progressPaint.setColor(Color.parseColor("#4CAF50"));
                progressPaint.setStyle(Paint.Style.FILL);
                float barWidth = w * 0.6f, barHeight = 20;
                float barX = (w - barWidth) / 2, barY = h - 60;
                canvas.drawRect(barX, barY, barX + barWidth * progress, barY + barHeight, progressPaint);
                Paint progressBg = new Paint();
                progressBg.setColor(Color.argb(80, 255, 255, 255));
                progressBg.setStyle(Paint.Style.STROKE);
                progressBg.setStrokeWidth(3f);
                canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, progressBg);
            }
        }
    }
}
