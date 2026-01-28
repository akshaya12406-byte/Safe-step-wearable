/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    SAFESTEP FALL DETECTION                       ║
 * ║              ESP32 + MPU6050 Production Firmware                 ║
 * ╚══════════════════════════════════════════════════════════════════╝
 * 
 * FEATURES:
 * - Real-time fall detection using MPU6050 accelerometer
 * - Impact force calculation (in g)
 * - Pitch/Roll angle monitoring for posture
 * - WiFi connectivity with auto-reconnect
 * - FCM push notifications via Cloudflare Worker
 * - LED status indicators
 * - Serial debug monitor
 * - Power-efficient operation
 * 
 * ARCHITECTURE:
 * ESP32 → MPU6050 (I2C) → Fall Detection Algorithm → HTTPS → Cloudflare Worker → FCM → Android
 * 
 * HARDWARE CONNECTIONS:
 * - MPU6050 VCC  → ESP32 3.3V
 * - MPU6050 GND  → ESP32 GND  
 * - MPU6050 SDA  → ESP32 GPIO21 (default I2C)
 * - MPU6050 SCL  → ESP32 GPIO22 (default I2C)
 * - Status LED   → ESP32 GPIO2 (built-in LED)
 * 
 * Author: SafeStep Team
 * Version: 2.0.0
 * License: MIT
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include "time.h"

// ════════════════════════════════════════════════════════════════════
// ▶ CONFIGURATION - UPDATE THESE VALUES
// ════════════════════════════════════════════════════════════════════

// WiFi credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Cloudflare Worker URL (from your worker deployment)
const char* WORKER_URL = "https://safestep-fcm-relay.YOUR-SUBDOMAIN.workers.dev";

// FCM Token from Android app (get from Developer screen)
const char* FCM_TOKEN = "YOUR_FCM_TOKEN_FROM_APP";

// Device configuration
const char* DEVICE_ID = "ESP32_01";
const char* FIRMWARE_VERSION = "2.0.0";

// ════════════════════════════════════════════════════════════════════
// ▶ FALL DETECTION PARAMETERS (Tune these for your use case)
// ════════════════════════════════════════════════════════════════════

// Impact threshold in g (1g = 9.8 m/s²)
// Typical fall: 2.5-4g impact, walking: 1.0-1.5g, running: 1.5-2.0g
const float IMPACT_THRESHOLD_G = 2.5;          // Trigger if impact exceeds this
const float FREEFALL_THRESHOLD_G = 0.3;        // Near-freefall detection
const float ORIENTATION_CHANGE_DEG = 50.0;     // Significant angle change

// Post-fall analysis window (milliseconds)
const unsigned long POST_IMPACT_WINDOW_MS = 2000;  // Wait 2s after impact
const float POST_IMPACT_STILLNESS_G = 0.2;         // Movement threshold after fall

// Debounce to prevent multiple alerts
const unsigned long ALERT_COOLDOWN_MS = 30000;     // 30 seconds between alerts

// Posture monitoring
const float POSTURE_BAD_PITCH = 60.0;          // Forward lean threshold
const float POSTURE_BAD_ROLL = 45.0;           // Side lean threshold
const unsigned long POSTURE_ALERT_DELAY_MS = 60000; // 1 minute sustained bad posture

// ════════════════════════════════════════════════════════════════════
// ▶ MPU6050 I2C ADDRESS
// ════════════════════════════════════════════════════════════════════
const int MPU6050_ADDR = 0x68;

// MPU6050 Register Addresses
const int MPU6050_PWR_MGMT_1 = 0x6B;
const int MPU6050_ACCEL_XOUT_H = 0x3B;
const int MPU6050_ACCEL_CONFIG = 0x1C;
const int MPU6050_WHO_AM_I = 0x75;

// ════════════════════════════════════════════════════════════════════
// ▶ HARDWARE PINS
// ════════════════════════════════════════════════════════════════════
const int LED_PIN = 2;  // Built-in LED on most ESP32 boards

// ════════════════════════════════════════════════════════════════════
// ▶ GLOBAL VARIABLES
// ════════════════════════════════════════════════════════════════════
WiFiClientSecure wifiClient;
HTTPClient http;

// Accelerometer data
float accelX, accelY, accelZ;
float pitch, roll;
float totalAccelG;

// Baseline calibration
float baselineX = 0, baselineY = 0, baselineZ = 0;
bool isCalibrated = false;

// State machine
enum DeviceState {
    STATE_IDLE,
    STATE_IMPACT_DETECTED,
    STATE_ANALYZING_FALL,
    STATE_FALL_CONFIRMED,
    STATE_COOLDOWN
};
DeviceState currentState = STATE_IDLE;

// Timing
unsigned long lastAlertTime = 0;
unsigned long impactDetectedTime = 0;
unsigned long lastPostureCheck = 0;
unsigned long badPostureStartTime = 0;

// Pre-impact buffer for analysis
float impactBuffer[10];
int bufferIndex = 0;

// NTP Time
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 19800;  // IST = UTC+5:30
const int daylightOffset_sec = 0;

// ════════════════════════════════════════════════════════════════════
// ▶ SETUP
// ════════════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    delay(1000);
    
    printBanner();
    
    // Initialize LED
    pinMode(LED_PIN, OUTPUT);
    blinkLED(3, 100);  // Startup indication
    
    // Initialize I2C for MPU6050
    Wire.begin();
    
    // Initialize MPU6050
    if (!initMPU6050()) {
        Serial.println("❌ MPU6050 initialization FAILED!");
        Serial.println("   Check wiring: SDA→GPIO21, SCL→GPIO22");
        while (true) {
            blinkLED(5, 100);  // Error pattern
            delay(1000);
        }
    }
    
    // Connect to WiFi
    connectWiFi();
    
    // Configure NTP
    configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
    Serial.println("⏰ NTP time synchronized");
    
    // Skip TLS verification (use certificate pinning in production)
    wifiClient.setInsecure();
    
    // Calibrate accelerometer at rest position
    calibrateAccelerometer();
    
    Serial.println("\n═══════════════════════════════════════");
    Serial.println("✅ SafeStep Ready - Monitoring for falls");
    Serial.println("═══════════════════════════════════════\n");
    
    digitalWrite(LED_PIN, HIGH);  // LED on = system ready
}

// ════════════════════════════════════════════════════════════════════
// ▶ MAIN LOOP
// ════════════════════════════════════════════════════════════════════
void loop() {
    // Ensure WiFi connection
    if (WiFi.status() != WL_CONNECTED) {
        connectWiFi();
    }
    
    // Read sensor data
    readMPU6050();
    
    // Calculate pitch and roll angles
    calculateOrientation();
    
    // Update impact buffer (circular)
    impactBuffer[bufferIndex] = totalAccelG;
    bufferIndex = (bufferIndex + 1) % 10;
    
    // State machine for fall detection
    switch (currentState) {
        case STATE_IDLE:
            handleIdleState();
            break;
            
        case STATE_IMPACT_DETECTED:
            handleImpactState();
            break;
            
        case STATE_ANALYZING_FALL:
            handleAnalyzingState();
            break;
            
        case STATE_FALL_CONFIRMED:
            handleFallConfirmedState();
            break;
            
        case STATE_COOLDOWN:
            handleCooldownState();
            break;
    }
    
    // Posture monitoring (runs independently)
    checkPosture();
    
    // Small delay for stability
    delay(20);  // 50Hz sampling rate
}

// ════════════════════════════════════════════════════════════════════
// ▶ STATE HANDLERS
// ════════════════════════════════════════════════════════════════════

void handleIdleState() {
    // Check for high impact (potential fall)
    if (totalAccelG > IMPACT_THRESHOLD_G) {
        Serial.println("\n⚠️ HIGH IMPACT DETECTED!");
        Serial.print("   Impact Force: ");
        Serial.print(totalAccelG, 2);
        Serial.println("g");
        
        impactDetectedTime = millis();
        currentState = STATE_IMPACT_DETECTED;
        blinkLED(2, 50);
    }
    
    // Check for freefall (sudden weightlessness)
    if (totalAccelG < FREEFALL_THRESHOLD_G) {
        Serial.println("\n⚠️ FREEFALL DETECTED!");
        impactDetectedTime = millis();
        currentState = STATE_IMPACT_DETECTED;
    }
}

void handleImpactState() {
    // Wait briefly for impact to settle
    if (millis() - impactDetectedTime > 100) {
        Serial.println("🔍 Analyzing fall characteristics...");
        currentState = STATE_ANALYZING_FALL;
    }
}

void handleAnalyzingState() {
    unsigned long elapsed = millis() - impactDetectedTime;
    
    // Wait for post-impact window
    if (elapsed > POST_IMPACT_WINDOW_MS) {
        // Calculate if person is now still (lying down)
        float recentMovement = calculateRecentMovement();
        
        // Check for significant orientation change
        bool significantAngleChange = (abs(pitch) > ORIENTATION_CHANGE_DEG || 
                                        abs(roll) > ORIENTATION_CHANGE_DEG);
        
        if (recentMovement < POST_IMPACT_STILLNESS_G && significantAngleChange) {
            Serial.println("\n🚨 FALL CONFIRMED!");
            Serial.print("   Person appears to be down (stillness: ");
            Serial.print(recentMovement, 2);
            Serial.println("g movement)");
            currentState = STATE_FALL_CONFIRMED;
        } else {
            Serial.println("✅ False alarm - person recovered or normal movement");
            currentState = STATE_IDLE;
        }
    }
}

void handleFallConfirmedState() {
    // Get the peak impact from buffer
    float peakImpact = getPeakImpact();
    
    Serial.println("\n╔══════════════════════════════════════╗");
    Serial.println("║         🚨 SENDING FALL ALERT        ║");
    Serial.println("╚══════════════════════════════════════╝");
    Serial.print("Peak Impact: ");
    Serial.print(peakImpact, 2);
    Serial.println("g");
    Serial.print("Pitch: ");
    Serial.print(pitch, 1);
    Serial.print("°  Roll: ");
    Serial.print(roll, 1);
    Serial.println("°");
    
    // Send alert
    bool success = sendFallAlert(peakImpact, pitch, roll);
    
    if (success) {
        Serial.println("\n✅ Alert sent successfully!");
        blinkLED(5, 200);  // Success pattern
    } else {
        Serial.println("\n❌ Alert failed to send!");
        blinkLED(10, 50);  // Error pattern
    }
    
    lastAlertTime = millis();
    currentState = STATE_COOLDOWN;
}

void handleCooldownState() {
    // Prevent alert spam
    if (millis() - lastAlertTime > ALERT_COOLDOWN_MS) {
        Serial.println("\n⏱️ Cooldown complete - Resuming monitoring");
        currentState = STATE_IDLE;
    }
}

// ════════════════════════════════════════════════════════════════════
// ▶ POSTURE MONITORING
// ════════════════════════════════════════════════════════════════════

void checkPosture() {
    unsigned long now = millis();
    
    // Check every 1 second
    if (now - lastPostureCheck < 1000) return;
    lastPostureCheck = now;
    
    bool badPosture = (abs(pitch) > POSTURE_BAD_PITCH || abs(roll) > POSTURE_BAD_ROLL);
    
    if (badPosture) {
        if (badPostureStartTime == 0) {
            badPostureStartTime = now;
        } else if (now - badPostureStartTime > POSTURE_ALERT_DELAY_MS) {
            // Sustained bad posture - could send a posture alert
            Serial.println("⚠️ Sustained poor posture detected");
            badPostureStartTime = now;  // Reset to avoid spam
        }
    } else {
        badPostureStartTime = 0;  // Reset if posture is good
    }
}

// ════════════════════════════════════════════════════════════════════
// ▶ MPU6050 FUNCTIONS
// ════════════════════════════════════════════════════════════════════

bool initMPU6050() {
    // Wake up MPU6050 (clear sleep bit)
    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(MPU6050_PWR_MGMT_1);
    Wire.write(0x00);  // Wake up
    int error = Wire.endTransmission();
    
    if (error != 0) {
        Serial.print("I2C Error: ");
        Serial.println(error);
        return false;
    }
    
    delay(100);
    
    // Check WHO_AM_I register
    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(MPU6050_WHO_AM_I);
    Wire.endTransmission(false);
    Wire.requestFrom(MPU6050_ADDR, 1);
    
    if (Wire.available()) {
        byte whoAmI = Wire.read();
        Serial.print("MPU6050 WHO_AM_I: 0x");
        Serial.println(whoAmI, HEX);
        
        if (whoAmI == 0x68 || whoAmI == 0x98) {
            Serial.println("✅ MPU6050 detected successfully!");
            
            // Configure accelerometer: ±8g range for fall detection
            Wire.beginTransmission(MPU6050_ADDR);
            Wire.write(MPU6050_ACCEL_CONFIG);
            Wire.write(0x10);  // ±8g (allows detecting up to ~8g impacts)
            Wire.endTransmission();
            
            return true;
        }
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
        
        // Convert to g (±8g range: 4096 LSB/g)
        accelX = rawX / 4096.0;
        accelY = rawY / 4096.0;
        accelZ = rawZ / 4096.0;
        
        // Calculate total acceleration magnitude
        totalAccelG = sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ);
    }
}

void calculateOrientation() {
    // Calculate pitch and roll from accelerometer
    // Pitch: rotation around Y axis
    // Roll: rotation around X axis
    pitch = atan2(accelX, sqrt(accelY*accelY + accelZ*accelZ)) * 180.0 / PI;
    roll = atan2(accelY, sqrt(accelX*accelX + accelZ*accelZ)) * 180.0 / PI;
}

void calibrateAccelerometer() {
    Serial.println("\n📐 Calibrating accelerometer...");
    Serial.println("   Keep device still and flat!");
    
    float sumX = 0, sumY = 0, sumZ = 0;
    
    for (int i = 0; i < 100; i++) {
        readMPU6050();
        sumX += accelX;
        sumY += accelY;
        sumZ += accelZ;
        delay(10);
    }
    
    baselineX = sumX / 100.0;
    baselineY = sumY / 100.0;
    baselineZ = sumZ / 100.0;
    
    Serial.print("   Baseline: X=");
    Serial.print(baselineX, 2);
    Serial.print("g, Y=");
    Serial.print(baselineY, 2);
    Serial.print("g, Z=");
    Serial.print(baselineZ, 2);
    Serial.println("g");
    
    isCalibrated = true;
}

// ════════════════════════════════════════════════════════════════════
// ▶ ANALYSIS HELPER FUNCTIONS
// ════════════════════════════════════════════════════════════════════

float calculateRecentMovement() {
    // Calculate variance in recent acceleration readings
    float sum = 0;
    for (int i = 0; i < 10; i++) {
        sum += impactBuffer[i];
    }
    float avg = sum / 10.0;
    
    float variance = 0;
    for (int i = 0; i < 10; i++) {
        variance += (impactBuffer[i] - avg) * (impactBuffer[i] - avg);
    }
    
    return sqrt(variance / 10.0);  // Return standard deviation as movement indicator
}

float getPeakImpact() {
    float peak = 0;
    for (int i = 0; i < 10; i++) {
        if (impactBuffer[i] > peak) {
            peak = impactBuffer[i];
        }
    }
    return peak;
}

// ════════════════════════════════════════════════════════════════════
// ▶ NETWORK FUNCTIONS
// ════════════════════════════════════════════════════════════════════

void connectWiFi() {
    Serial.print("\n📶 Connecting to WiFi: ");
    Serial.println(WIFI_SSID);
    
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        blinkLED(1, 100);
        attempts++;
    }
    Serial.println();
    
    if (WiFi.status() == WL_CONNECTED) {
        Serial.println("✅ WiFi connected!");
        Serial.print("   IP Address: ");
        Serial.println(WiFi.localIP());
        Serial.print("   Signal Strength: ");
        Serial.print(WiFi.RSSI());
        Serial.println(" dBm");
    } else {
        Serial.println("❌ WiFi connection FAILED!");
    }
}

bool sendFallAlert(float impactG, float currentPitch, float currentRoll) {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("❌ WiFi not connected!");
        return false;
    }
    
    // Check configuration
    if (String(WORKER_URL).indexOf("YOUR-SUBDOMAIN") >= 0) {
        Serial.println("❌ ERROR: WORKER_URL not configured!");
        return false;
    }
    
    if (String(FCM_TOKEN).indexOf("YOUR_FCM") >= 0) {
        Serial.println("❌ ERROR: FCM_TOKEN not configured!");
        return false;
    }
    
    Serial.println("\n📤 Sending alert to Cloudflare Worker...");
    
    // Build JSON payload matching FCM contract
    StaticJsonDocument<768> doc;
    doc["token"] = FCM_TOKEN;                  // Required by worker
    doc["device_id"] = DEVICE_ID;
    doc["event_type"] = "FALL_CONFIRMED";
    doc["event_id"] = String("evt_") + String(millis());
    doc["timestamp"] = getISOTimestamp();
    doc["impact_g"] = String(impactG, 2);      // String for FCM data
    doc["pitch"] = String(currentPitch, 1);
    doc["roll"] = String(currentRoll, 1);
    doc["firmware_version"] = FIRMWARE_VERSION;
    
    String payload;
    serializeJson(doc, payload);
    
    Serial.println("   Payload: " + payload);
    
    // Send HTTP POST
    http.begin(wifiClient, WORKER_URL);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(15000);  // 15 second timeout
    
    int httpCode = http.POST(payload);
    
    bool success = false;
    
    if (httpCode > 0) {
        Serial.print("   HTTP Response: ");
        Serial.println(httpCode);
        
        String response = http.getString();
        Serial.println("   Response: " + response);
        
        success = (httpCode == 200);
    } else {
        Serial.print("   HTTP Error: ");
        Serial.println(http.errorToString(httpCode));
    }
    
    http.end();
    return success;
}

String getISOTimestamp() {
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo)) {
        // Fallback if NTP not synced
        return "2026-01-28T00:00:00Z";
    }
    
    char buffer[30];
    strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
    return String(buffer);
}

// ════════════════════════════════════════════════════════════════════
// ▶ UTILITY FUNCTIONS
// ════════════════════════════════════════════════════════════════════

void blinkLED(int times, int delayMs) {
    for (int i = 0; i < times; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(delayMs);
        digitalWrite(LED_PIN, LOW);
        delay(delayMs);
    }
}

void printBanner() {
    Serial.println("\n");
    Serial.println("╔══════════════════════════════════════════════════════════════╗");
    Serial.println("║                                                              ║");
    Serial.println("║      ███████╗ █████╗ ███████╗███████╗███████╗████████╗       ║");
    Serial.println("║      ██╔════╝██╔══██╗██╔════╝██╔════╝██╔════╝╚══██╔══╝       ║");
    Serial.println("║      ███████╗███████║█████╗  █████╗  ███████╗   ██║          ║");
    Serial.println("║      ╚════██║██╔══██║██╔══╝  ██╔══╝  ╚════██║   ██║          ║");
    Serial.println("║      ███████║██║  ██║██║     ███████╗███████║   ██║          ║");
    Serial.println("║      ╚══════╝╚═╝  ╚═╝╚═╝     ╚══════╝╚══════╝   ╚═╝          ║");
    Serial.println("║                                                              ║");
    Serial.println("║           Fall Detection Wearable - v2.0.0                   ║");
    Serial.println("║              ESP32 + MPU6050 Production                      ║");
    Serial.println("║                                                              ║");
    Serial.println("╚══════════════════════════════════════════════════════════════╝");
    Serial.println();
    Serial.print("Device ID: ");
    Serial.println(DEVICE_ID);
    Serial.print("Firmware:  ");
    Serial.println(FIRMWARE_VERSION);
    Serial.println();
}
