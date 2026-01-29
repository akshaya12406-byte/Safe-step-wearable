/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    SAFESTEP FALL DETECTION                       ║
 * ║              ESP32 + MPU6050 Production Firmware                 ║
 * ║                     v3.0 - CAREGIVER MODE                        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 * 
 * FEATURES:
 * - Delta-based fall detection (superior accuracy)
 * - HTTP retry with RSSI warnings
 * - NTP time sync
 * - POSTURE MONITORING (sends every 30s)
 * - Multi-recipient FCM via Worker v3.0
 * 
 * HARDWARE CONNECTIONS:
 * - MPU6050 VCC  → ESP32 3.3V
 * - MPU6050 GND  → ESP32 GND  
 * - MPU6050 SDA  → ESP32 GPIO21
 * - MPU6050 SCL  → ESP32 GPIO22
 * 
 * Version: 3.0.0 CAREGIVER
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include "time.h"
#include <math.h>

// ════════════════════════════════════════════════════════════════════
// ▶ YOUR CONFIGURATION
// ════════════════════════════════════════════════════════════════════

const char* WIFI_SSID = "Harish";
const char* WIFI_PASSWORD = "Harish0519";
const char* WORKER_URL = "https://safestep-fcm-relay.harishkumar-sp5511.workers.dev";
const char* DEVICE_ID = "ESP32_01";
const char* FIRMWARE_VERSION = "3.0.0";

// Posture monitoring settings
const bool POSTURE_ENABLED = true;
const unsigned long POSTURE_INTERVAL_MS = 30000;  // 30 seconds

// ════════════════════════════════════════════════════════════════════
// ▶ FALL DETECTION PARAMETERS
// ════════════════════════════════════════════════════════════════════

const float IMPACT_THRESHOLD_G = 2.5;
const float FREEFALL_THRESHOLD_G = 0.3;
const float ORIENTATION_CHANGE_DEG = 50.0;
const unsigned long POST_IMPACT_WINDOW_MS = 2000;
const float POST_IMPACT_STILLNESS_G = 0.25;
const unsigned long ALERT_COOLDOWN_MS = 30000;
const int HTTP_RETRY_COUNT = 2;
const int RSSI_WARNING_THRESHOLD = -80;

// ════════════════════════════════════════════════════════════════════
// ▶ MPU6050 CONFIGURATION
// ════════════════════════════════════════════════════════════════════
const int MPU6050_ADDR = 0x68;
const int MPU6050_PWR_MGMT_1 = 0x6B;
const int MPU6050_ACCEL_XOUT_H = 0x3B;
const int MPU6050_ACCEL_CONFIG = 0x1C;
const int MPU6050_WHO_AM_I = 0x75;
const int LED_PIN = 2;

// ════════════════════════════════════════════════════════════════════
// ▶ GLOBAL VARIABLES
// ════════════════════════════════════════════════════════════════════
WiFiClientSecure wifiClient;
HTTPClient http;

float accelX = 0, accelY = 0, accelZ = 0;
float pitch = 0, roll = 0;
float totalAccelG = 0;
float baselineX = 0, baselineY = 0, baselineZ = 0;
float baselineMag = 1.0;
bool isCalibrated = false;

enum DeviceState { STATE_IDLE, STATE_IMPACT_DETECTED, STATE_ANALYZING_FALL, STATE_FALL_CONFIRMED, STATE_COOLDOWN };
DeviceState currentState = STATE_IDLE;

unsigned long lastAlertTime = 0;
unsigned long impactDetectedTime = 0;
unsigned long lastPostureTime = 0;

float impactBuffer[10];
int bufferIndex = 0;

const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 19800;  // IST
const int daylightOffset_sec = 0;

// ════════════════════════════════════════════════════════════════════
// ▶ SETUP
// ════════════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    delay(500);
    
    printBanner();
    
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    blinkLED(2, 100);
    
    Wire.begin();
    
    if (!initMPU6050()) {
        Serial.println("❌ MPU6050 FAILED! Check: SDA→21, SCL→22");
        while (true) { blinkLED(5, 100); delay(1000); }
    }
    
    connectWiFi();
    configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
    wifiClient.setInsecure();
    calibrateAccelerometer();
    
    for (int i = 0; i < 10; i++) {
        readMPU6050();
        impactBuffer[i] = fabs(totalAccelG - baselineMag);
        delay(20);
    }
    
    Serial.println("\n═══════════════════════════════════════");
    Serial.println("✅ SafeStep v3.0 CAREGIVER MODE Ready!");
    Serial.println("═══════════════════════════════════════\n");
    blinkLED(3, 150);
}

// ════════════════════════════════════════════════════════════════════
// ▶ MAIN LOOP
// ════════════════════════════════════════════════════════════════════
void loop() {
    if (WiFi.status() != WL_CONNECTED) connectWiFi();
    
    readMPU6050();
    calculateOrientation();
    
    float deltaG = fabs(totalAccelG - baselineMag);
    impactBuffer[bufferIndex] = deltaG;
    bufferIndex = (bufferIndex + 1) % 10;
    
    // ---- FALL DETECTION STATE MACHINE ----
    switch (currentState) {
        case STATE_IDLE:
            if (deltaG > IMPACT_THRESHOLD_G) {
                Serial.printf("\n⚠️ IMPACT! Delta: %.2fg\n", deltaG);
                impactDetectedTime = millis();
                currentState = STATE_IMPACT_DETECTED;
                blinkLED(1, 80);
            } else if (totalAccelG < FREEFALL_THRESHOLD_G) {
                Serial.println("\n⚠️ FREEFALL!");
                impactDetectedTime = millis();
                currentState = STATE_IMPACT_DETECTED;
            }
            break;
            
        case STATE_IMPACT_DETECTED:
            if (millis() - impactDetectedTime > 100) {
                Serial.println("🔍 Analyzing...");
                currentState = STATE_ANALYZING_FALL;
            }
            break;
            
        case STATE_ANALYZING_FALL:
            if (millis() - impactDetectedTime > POST_IMPACT_WINDOW_MS) {
                float movement = calculateRecentMovement();
                bool angleChange = (fabs(pitch) > ORIENTATION_CHANGE_DEG || fabs(roll) > ORIENTATION_CHANGE_DEG);
                
                if (movement < POST_IMPACT_STILLNESS_G && angleChange) {
                    Serial.println("🚨 FALL CONFIRMED!");
                    currentState = STATE_FALL_CONFIRMED;
                } else {
                    Serial.println("✅ False alarm");
                    currentState = STATE_IDLE;
                }
            }
            break;
            
        case STATE_FALL_CONFIRMED: {
            float peak = getPeakImpact();
            Serial.printf("\n╔═══ SENDING ALERT ═══╗\nImpact: %.2fg\nPitch: %.1f° Roll: %.1f°\n", peak, pitch, roll);
            
            digitalWrite(LED_PIN, HIGH);
            bool ok = sendFallAlert(peak, pitch, roll);
            digitalWrite(LED_PIN, LOW);
            
            if (ok) {
                Serial.println("✅ Alert sent to caregivers!");
                blinkLED(3, 200);
            } else {
                Serial.println("❌ Alert failed!");
                blinkLED(8, 50);
            }
            
            lastAlertTime = millis();
            currentState = STATE_COOLDOWN;
            break;
        }
            
        case STATE_COOLDOWN:
            if (millis() - lastAlertTime > ALERT_COOLDOWN_MS) {
                Serial.println("⏱️ Ready again");
                currentState = STATE_IDLE;
            }
            break;
    }
    
    // ---- POSTURE MONITORING (every 30s) ----
    if (POSTURE_ENABLED && (millis() - lastPostureTime > POSTURE_INTERVAL_MS)) {
        sendPosture();
        lastPostureTime = millis();
    }
    
    delay(20);
}

// ════════════════════════════════════════════════════════════════════
// ▶ POSTURE SENDING
// ════════════════════════════════════════════════════════════════════

void sendPosture() {
    if (WiFi.status() != WL_CONNECTED) return;
    
    // Determine posture state based on pitch
    String postureState = "GOOD";
    if (fabs(pitch) > 45) {
        postureState = "POOR";
    } else if (fabs(pitch) > 30) {
        postureState = "FAIR";
    }
    
    StaticJsonDocument<256> doc;
    doc["device_id"] = DEVICE_ID;
    doc["posture_state"] = postureState;
    doc["pitch"] = pitch;
    doc["roll"] = roll;
    doc["timestamp"] = getISOTimestamp();
    
    String payload;
    serializeJson(doc, payload);
    
    String postureUrl = String(WORKER_URL) + "/writePosture";
    
    http.begin(wifiClient, postureUrl);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(10000);
    
    int code = http.POST(payload);
    
    if (code == 200) {
        Serial.printf("📐 Posture sent: %s (pitch=%.1f°)\n", postureState.c_str(), pitch);
    } else {
        Serial.printf("⚠️ Posture send failed: %d\n", code);
    }
    
    http.end();
}

// ════════════════════════════════════════════════════════════════════
// ▶ MPU6050 FUNCTIONS
// ════════════════════════════════════════════════════════════════════

bool initMPU6050() {
    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(MPU6050_PWR_MGMT_1);
    Wire.write(0x00);
    if (Wire.endTransmission() != 0) return false;
    delay(100);
    
    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(MPU6050_WHO_AM_I);
    Wire.endTransmission(false);
    Wire.requestFrom(MPU6050_ADDR, 1);
    
    if (Wire.available()) {
        byte who = Wire.read();
        Serial.printf("MPU6050 ID: 0x%X ✅\n", who);
        
        Wire.beginTransmission(MPU6050_ADDR);
        Wire.write(MPU6050_ACCEL_CONFIG);
        Wire.write(0x10); // ±8g
        Wire.endTransmission();
        return true;
    }
    return false;
}

void readMPU6050() {
    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(MPU6050_ACCEL_XOUT_H);
    Wire.endTransmission(false);
    Wire.requestFrom(MPU6050_ADDR, 6);
    
    if (Wire.available() >= 6) {
        int16_t rawX = Wire.read() << 8 | Wire.read();
        int16_t rawY = Wire.read() << 8 | Wire.read();
        int16_t rawZ = Wire.read() << 8 | Wire.read();
        
        accelX = rawX / 4096.0;
        accelY = rawY / 4096.0;
        accelZ = rawZ / 4096.0;
        totalAccelG = sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ);
    }
}

void calculateOrientation() {
    pitch = atan2(accelX, sqrt(accelY*accelY + accelZ*accelZ)) * 180.0 / PI;
    roll = atan2(accelY, sqrt(accelX*accelX + accelZ*accelZ)) * 180.0 / PI;
}

void calibrateAccelerometer() {
    Serial.println("\n📐 Calibrating... keep still!");
    float sumX = 0, sumY = 0, sumZ = 0;
    
    for (int i = 0; i < 100; i++) {
        readMPU6050();
        sumX += accelX; sumY += accelY; sumZ += accelZ;
        delay(10);
    }
    
    baselineX = sumX / 100.0;
    baselineY = sumY / 100.0;
    baselineZ = sumZ / 100.0;
    baselineMag = sqrt(baselineX*baselineX + baselineY*baselineY + baselineZ*baselineZ);
    
    Serial.printf("Baseline: %.3fg (expected ~1.0g)\n", baselineMag);
    isCalibrated = true;
}

float calculateRecentMovement() {
    float sum = 0, avg = 0, variance = 0;
    for (int i = 0; i < 10; i++) sum += impactBuffer[i];
    avg = sum / 10.0;
    for (int i = 0; i < 10; i++) variance += (impactBuffer[i] - avg) * (impactBuffer[i] - avg);
    return sqrt(variance / 10.0);
}

float getPeakImpact() {
    float peak = 0;
    for (int i = 0; i < 10; i++) if (impactBuffer[i] > peak) peak = impactBuffer[i];
    return peak;
}

// ════════════════════════════════════════════════════════════════════
// ▶ NETWORK FUNCTIONS
// ════════════════════════════════════════════════════════════════════

void connectWiFi() {
    Serial.printf("\n📶 Connecting to %s", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 40) {
        delay(300);
        Serial.print(".");
        attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("\n✅ Connected! IP: %s RSSI: %d dBm\n", 
            WiFi.localIP().toString().c_str(), WiFi.RSSI());
    } else {
        Serial.println("\n❌ WiFi failed!");
    }
}

bool sendFallAlert(float impactG, float currentPitch, float currentRoll) {
    if (WiFi.status() != WL_CONNECTED) return false;
    
    if (WiFi.RSSI() < RSSI_WARNING_THRESHOLD) {
        Serial.printf("⚠️ Weak signal: %d dBm\n", WiFi.RSSI());
    }
    
    StaticJsonDocument<512> doc;
    doc["device_id"] = DEVICE_ID;  // Worker v3.0 uses this to lookup caregivers
    doc["event_type"] = "FALL_CONFIRMED";
    doc["event_id"] = String("evt_") + String(millis());
    doc["timestamp"] = getISOTimestamp();
    doc["impact_g"] = String(impactG, 2);
    doc["pitch"] = String(currentPitch, 1);
    doc["roll"] = String(currentRoll, 1);
    doc["firmware_version"] = FIRMWARE_VERSION;
    
    String payload;
    serializeJson(doc, payload);
    Serial.println("📤 " + payload);
    
    bool success = false;
    for (int attempt = 1; attempt <= HTTP_RETRY_COUNT && !success; attempt++) {
        Serial.printf("Attempt %d/%d...\n", attempt, HTTP_RETRY_COUNT);
        
        http.begin(wifiClient, WORKER_URL);
        http.addHeader("Content-Type", "application/json");
        http.setTimeout(15000);
        
        int code = http.POST(payload);
        if (code > 0) {
            Serial.printf("HTTP %d\n", code);
            String resp = http.getString();
            Serial.println(resp.substring(0, 200));
            success = (code == 200 || code == 201);
        } else {
            Serial.printf("Error: %s\n", http.errorToString(code).c_str());
        }
        http.end();
        
        if (!success && attempt < HTTP_RETRY_COUNT) delay(500);
    }
    
    return success;
}

String getISOTimestamp() {
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo, 1000)) {
        return String("unsynced-") + String(millis() / 1000);
    }
    char buf[30];
    strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
    return String(buf);
}

// ════════════════════════════════════════════════════════════════════
// ▶ UTILITIES
// ════════════════════════════════════════════════════════════════════

void blinkLED(int times, int ms) {
    for (int i = 0; i < times; i++) {
        digitalWrite(LED_PIN, HIGH); delay(ms);
        digitalWrite(LED_PIN, LOW); delay(ms);
    }
}

void printBanner() {
    Serial.println("\n╔═══════════════════════════════════════╗");
    Serial.println("║    SAFESTEP v3.0 CAREGIVER MODE       ║");
    Serial.println("║    ESP32 + MPU6050 Fall + Posture     ║");
    Serial.println("╚═══════════════════════════════════════╝");
    Serial.printf("Device: %s\nWorker: %s\n\n", DEVICE_ID, WORKER_URL);
}
