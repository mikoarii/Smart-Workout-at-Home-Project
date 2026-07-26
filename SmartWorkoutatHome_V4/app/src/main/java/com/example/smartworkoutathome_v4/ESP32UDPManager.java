package com.example.smartworkoutathome_v4;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ESP32UDPManager {

    private static final String TAG = "ESP32_UDP";
    private static final int ESP32_PORT = 4210;

    private static ESP32UDPManager instance;

    public static synchronized ESP32UDPManager getInstance() {
        if (instance == null) {
            instance = new ESP32UDPManager();
        }
        return instance;
    }

    public static class WorkoutData {
        private int pan, tilt;
        public void setServoPan(int p) { this.pan = p; }
        public void setServoTilt(int t) { this.tilt = t; }
        public int getPan() { return pan; }
        public int getTilt() { return tilt; }
    }

    private DatagramSocket udpSocket;
    private ExecutorService sendExecutor;
    private Thread receiveThread;
    private boolean isListening = false;
    private Handler mainHandler;
    private Handler heartbeatHandler;

    private final MutableLiveData<ConnectionState> connectionState = new MutableLiveData<>(ConnectionState.DISCONNECTED);

    private ConnectionListener listener;
    private long lastHeartbeatTime = 0;
    private static final long HEARTBEAT_TIMEOUT_MS = 15000;
    private boolean heartbeatCheckerRunning = false;

    private String esp32Ip = null;  // IP ESP32 akan diketahui dari pesan masuk
    private InetAddress esp32Address = null;

    public enum ConnectionState { DISCONNECTED, CONNECTED }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(String message);
    }

    private ESP32UDPManager() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.heartbeatHandler = new Handler(Looper.getMainLooper());
        this.sendExecutor = Executors.newSingleThreadExecutor();
        connect();
    }

    public void setConnectionListener(ConnectionListener listener) { this.listener = listener; }

    public synchronized void connect() {
        if (udpSocket != null && !udpSocket.isClosed()) return;
        sendExecutor.execute(() -> {
            try {
                udpSocket = new DatagramSocket(ESP32_PORT);  // Listen di port yang sama
                udpSocket.setBroadcast(true);
                isListening = true;
                startListening();
                startHeartbeatChecker();
                Log.d(TAG, "Socket UDP Aktif, listening on port " + ESP32_PORT);
            } catch (Exception e) {
                Log.e(TAG, "Gagal buka Socket: " + e.getMessage());
            }
        });
    }

    private void startListening() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[255];
            while (isListening && udpSocket != null && !udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    lastHeartbeatTime = System.currentTimeMillis();

                    InetAddress senderIp = packet.getAddress();
                    Log.d(TAG, "Received from " + senderIp.getHostAddress() + ": " + message);

                    // Tangkap IP ESP32 dari pesan
                    if (message.startsWith("ESP32_HERE:")) {
                        esp32Ip = message.substring(11);
                        esp32Address = InetAddress.getByName(esp32Ip);
                        Log.d(TAG, "ESP32 FOUND at IP: " + esp32Ip);
                        updateConnectionState(ConnectionState.CONNECTED);
                    }
                    else if (message.startsWith("ESP32_IP:")) {
                        esp32Ip = message.substring(9);
                        esp32Address = InetAddress.getByName(esp32Ip);
                        Log.d(TAG, "ESP32 FOUND at IP: " + esp32Ip);
                        updateConnectionState(ConnectionState.CONNECTED);
                    }
                    else if (message.equals("PONG") || message.startsWith("ACK:")) {
                        updateConnectionState(ConnectionState.CONNECTED);
                    }

                    final String finalMessage = message;
                    mainHandler.post(() -> handleIncomingMessage(finalMessage));

                } catch (Exception e) {
                    if (isListening) Log.e(TAG, "Receive error: " + e.getMessage());
                }
            }
        });
        receiveThread.start();
    }

    private void handleIncomingMessage(String message) {
        if (listener != null) listener.onMessageReceived(message);

        // Parse pesan dengan prefix
        if (message.startsWith("ESP32_HERE:")) {
            esp32Ip = message.substring(11);
            // ...
        }
        else if (message.startsWith("ACK:")) {
            String ack = message.substring(4);
            Log.d(TAG, "ACK received: " + ack);
            updateConnectionState(ConnectionState.CONNECTED);
        }
        else if (message.startsWith("MOVING:")) {
            Log.d(TAG, "ESP32 moving to: " + message.substring(7));
        }
        else if (message.startsWith("REACHED:")) {
            String pos = message.substring(8);
            Log.d(TAG, "ESP32 reached position: " + pos);
        }
        else if (message.equals("PONG")) {
            updateConnectionState(ConnectionState.CONNECTED);
        }
    }

    private void sendRawCommandInternal(String command) {
        try {
            if (esp32Address == null) {
                Log.w(TAG, "ESP32 address not yet known, cannot send: " + command);
                return;
            }

            byte[] data = command.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, esp32Address, ESP32_PORT);
            if (udpSocket != null) {
                udpSocket.send(packet);
                Log.d(TAG, "Sent to " + esp32Address.getHostAddress() + ": " + command);
            }
        } catch (Exception e) {
            Log.e(TAG, "Send error: " + e.getMessage());
        }
    }

    public boolean moveServo(int pan, int tilt) {
        return sendCommand("MOVE:" + pan + "," + tilt);
    }

    public void sendRawCommand(String command) {
        sendCommand(command);
    }

    public void freeze() {
        sendCommand("FREEZE");
    }

    public void unfreeze() {
        sendCommand("UNFREEZE");
    }

    public void center() {
        sendCommand("CENTER");
    }

    public boolean isConnected() {
        return connectionState.getValue() == ConnectionState.CONNECTED && esp32Address != null;
    }

    private boolean sendCommand(String cmd) {
        sendExecutor.execute(() -> sendRawCommandInternal(cmd));
        return true;
    }

    private void startHeartbeatChecker() {
        if (heartbeatCheckerRunning) return;
        heartbeatCheckerRunning = true;
        heartbeatHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - lastHeartbeatTime > HEARTBEAT_TIMEOUT_MS) {
                    updateConnectionState(ConnectionState.DISCONNECTED);
                }
                if (heartbeatCheckerRunning) heartbeatHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void updateConnectionState(ConnectionState state) {
        if (connectionState.getValue() != state) {
            mainHandler.post(() -> {
                connectionState.setValue(state);
                if (listener != null) {
                    if (state == ConnectionState.CONNECTED) listener.onConnected();
                    else listener.onDisconnected();
                }
            });
        }
    }

    public LiveData<ConnectionState> getConnectionState() { return connectionState; }

    public void cleanup() {
        heartbeatCheckerRunning = false;
        isListening = false;
        if (udpSocket != null) udpSocket.close();
        sendExecutor.shutdown();
    }
}