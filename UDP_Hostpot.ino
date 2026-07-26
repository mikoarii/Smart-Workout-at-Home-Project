#include <WiFi.h>
#include <WiFiUdp.h>
#include <ESP32Servo.h>

const char* ssid = "SmartWorkout";
const char* password = "12345678";

WiFiUDP udp;
unsigned int localPort = 4210;
char packetBuffer[255];

IPAddress hpIP;
unsigned int hpPort = 4210;
bool hpKnown = false;

#define PAN_PIN 22
#define TILT_PIN 23
#define LED_PIN 2

Servo pan;
Servo tilt;

const int PAN_MIN = 30;
const int PAN_MAX = 150;
const int TILT_MIN = 50;
const int TILT_MAX = 110;

int currentPan = 90;
int currentTilt = 90;
int targetPan = 90;
int targetTilt = 90;

bool servoFrozen = false;

unsigned long lastServoUpdate = 0;
const unsigned long SERVO_INTERVAL_MS = 15;
unsigned long lastSendTime = 0;
const unsigned long SEND_INTERVAL = 2000;

int validatePan(int value) { return constrain(value, PAN_MIN, PAN_MAX); }
int validateTilt(int value) { return constrain(value, TILT_MIN, TILT_MAX); }

// ========== PENGIRIMAN PESAN KE HP ==========
void sendToHP(String prefix, String message) {
    if (hpKnown && hpIP != IPAddress(0,0,0,0)) {
        String fullMessage = prefix + message;
        udp.beginPacket(hpIP, hpPort);
        udp.print(fullMessage);
        udp.endPacket();
        Serial.println("[SEND] " + fullMessage);
    }
}

void sendToHP(String message) {
    if (hpKnown && hpIP != IPAddress(0,0,0,0)) {
        udp.beginPacket(hpIP, hpPort);
        udp.print(message);
        udp.endPacket();
        Serial.println("[SEND] " + message);
    }
}

void sendToGateway(String message) {
    IPAddress gateway = WiFi.gatewayIP();
    if (gateway != IPAddress(0,0,0,0)) {
        udp.beginPacket(gateway, hpPort);
        udp.print(message);
        udp.endPacket();
        Serial.println("[SEND to Gateway] " + message + " -> " + gateway.toString());
    }
}

// ========== PROSES PERINTAH DARI HP ==========
void processCommand(String cmd, IPAddress senderIP, unsigned int senderPort) {
    if (cmd.length() == 0) return;
    
    Serial.println("[RECV] " + cmd);
    digitalWrite(LED_PIN, HIGH);
    
    // Simpan IP pengirim
    if (!hpKnown || hpIP != senderIP) {
        hpIP = senderIP;
        hpPort = senderPort;
        hpKnown = true;
        Serial.println("[HP DETECTED] IP: " + hpIP.toString());
        sendToHP("ESP32_IP:", WiFi.localIP().toString());
    }
    
    // PARSING PERINTAH
    if (cmd.startsWith("MOVE:")) {
        String params = cmd.substring(5);
        int comma = params.indexOf(',');
        if (comma > 0) {
            if (!servoFrozen) {
                int newPan = validatePan(params.substring(0, comma).toInt());
                int newTilt = validateTilt(params.substring(comma + 1).toInt());
                targetPan = newPan;
                targetTilt = newTilt;
                Serial.println("[MOVE] Target: Pan=" + String(targetPan) + " Tilt=" + String(targetTilt));
                sendToHP("MOVING:", String(targetPan) + "," + String(targetTilt));
            } else {
                sendToHP("ERROR:", "FROZEN");
            }
        }
    }
    else if (cmd == "FREEZE") { 
        servoFrozen = true; 
        sendToHP("ACK:", "FROZEN");
        Serial.println("[FREEZE] Servo frozen");
    }
    else if (cmd == "UNFREEZE") { 
        servoFrozen = false; 
        sendToHP("ACK:", "UNFROZEN");
        Serial.println("[UNFREEZE] Servo unfrozen");
    }
    else if (cmd == "CENTER") { 
        if(!servoFrozen){ 
            targetPan = 90;
            targetTilt = 90;
            sendToHP("ACK:", "CENTERED");
            Serial.println("[CENTER] Target set to 90,90");
        } else {
            sendToHP("ERROR:", "FROZEN");
        }
    }
    else if (cmd == "PING") { 
        sendToHP("PONG", "");
        Serial.println("[PING] PONG sent");
    }
    else if (cmd == "STATUS") {
        sendToHP("STATUS:", "P" + String(currentPan) + ",T" + String(currentTilt) + ",F" + (servoFrozen?"1":"0"));
    }
    
    digitalWrite(LED_PIN, LOW);
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    
    Serial.println("\n========================================");
    Serial.println("ESP32 UDP - WITH PREFIX SYSTEM");
    Serial.println("========================================\n");
    
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    
    pan.attach(PAN_PIN);
    tilt.attach(TILT_PIN);
    pan.write(currentPan);
    tilt.write(currentTilt);
    
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);
    
    Serial.print("Connecting to ");
    Serial.print(ssid);
    
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    
    Serial.println("\n[OK] Connected!");
    Serial.print("ESP32 IP: ");
    Serial.println(WiFi.localIP());
    Serial.print("Gateway: ");
    Serial.println(WiFi.gatewayIP());
    
    udp.begin(localPort);
    Serial.println("UDP Port: " + String(localPort));
    Serial.println("READY - Sending announcements...\n");
}

void loop() {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("WiFi disconnected!");
        digitalWrite(LED_PIN, LOW);
        WiFi.reconnect();
        delay(1000);
        return;
    }
    
    // KIRIM ANNOUNCE KE GATEWAY SETIAP 2 DETIK
    if (millis() - lastSendTime > SEND_INTERVAL) {
        lastSendTime = millis();
        sendToGateway("ESP32_HERE:" + WiFi.localIP().toString());
    }
    
    // BACA PERINTAH DARI HP
    int packetSize = udp.parsePacket();
    if (packetSize) {
        int len = udp.read(packetBuffer, 255);
        if (len > 0) packetBuffer[len] = 0;
        processCommand(String(packetBuffer), udp.remoteIP(), udp.remotePort());
    }
    
    // GERAKAN SERVO DENGAN FEEDBACK
    if (millis() - lastServoUpdate >= SERVO_INTERVAL_MS) {
        lastServoUpdate = millis();
        
        bool positionChanged = false;
        
        if (currentPan != targetPan) {
            if (currentPan < targetPan) currentPan++;
            else if (currentPan > targetPan) currentPan--;
            pan.write(currentPan);
            positionChanged = true;
        }
        
        if (currentTilt != targetTilt) {
            if (currentTilt < targetTilt) currentTilt++;
            else if (currentTilt > targetTilt) currentTilt--;
            tilt.write(currentTilt);
            positionChanged = true;
        }
        
        // FEEDBACK SAAT MENCAPAI TARGET
        if (positionChanged && currentPan == targetPan && currentTilt == targetTilt) {
            if (hpKnown) {
                sendToHP("REACHED:", String(currentPan) + "," + String(currentTilt));
                Serial.println("[REACHED] Pan=" + String(currentPan) + " Tilt=" + String(currentTilt));
            }
        }
    }
}