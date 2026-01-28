/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    SAFESTEP FALL DETECTION                       ║
 * ║              ESP32 + MPU6050 Production Firmware                 ║
 * ║                      VERSION 2.1.0 (FIXED)                       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 * 
 * FIXES in v2.1:
 * - Delta-based impact detection (vs baseline magnitude)
 * - Buffer prefill at startup
 * - HTTP retry logic (2 attempts)
 * - RSSI warning on weak signal
 * - Token length validation
 * - Accepts 200/201/202 as success
 * 
 * ARCHITECTURE:
 * ESP32 → MPU6050 (I2C) → Fall Detection Algorithm → HTTPS → Cloudflare Worker → FCM → Android
 * 
 * Author: SafeStep Team
 * Version: 2.1.0
 * License: MIT
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include "time.h"
#include <math.h>

// ════════════════════════════════════════════════════════════════════
// ▶ CONFIGURATION - UPDATE THESE VALUES
// ════════════════════════════════════════════════════════════════════

// WiFi credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Cloudflare Worker URL (NO trailing slash!)
const char* WORKER_URL = "https://safestep-fcm-relay.harishkumar-sp5511.workers.dev";

// FCM Token from Android app (get from Developer screen)
const char* FCM_TOKEN = "YOUR_FCM_TOKEN_FROM_APP";

// Device configuration
const char* DEVICE_ID = "ESP32_01";
const char* FIRMWARE_VERSION = "2.1.0";

// ════════════════════════════════════════════════════════════════════
// ▶ OPTIONAL FEATURES (compile-time flags)
// ════════════════════════════════════════════════════════════════════
#define SEND_POSTURE 0              // Set to 1 to enable posture reporting
#define POSTURE_INTERVAL_SEC 60     // Seconds between posture reports

// ════════════════════════════════════════════════════════════════════
// ▶ FALL DETECTION PARAMETERS (Tune these for your use case)
// ════════════════════════════════════════════════════════════════════

// Impact threshold in g (DELTA from baseline, not absolute)
const float IMPACT_THRESHOLD_G = 2.5;          // Trigger if delta exceeds this
const float FREEFALL_THRESHOLD_G = 0.3;        // Near-freefall detection
const float ORIENTATION_CHANGE_DEG = 50.0;     // Significant angle change

// Post-fall analysis window (milliseconds)
const unsigned long POST_IMPACT_WINDOW_MS = 2000;  // Wait 2s after impact
const float POST_IMPACT_STILLNESS_G = 0.25;        // Movement threshold after fall

// Debounce to prevent multiple alerts
const unsigned long ALERT_COOLDOWN_MS = 30000;     // 30 seconds between alerts

// Posture monitoring
const float POSTURE_BAD_PITCH = 60.0;          // Forward lean threshold
const float POSTURE_BAD_ROLL = 45.0;           // Side lean threshold
const unsigned long POSTURE_ALERT_DELAY_MS = 60000; // 1 minute sustained bad posture

// Network
const int HTTP_RETRY_COUNT = 2;                // Number of retry attempts
const int RSSI_WARNING_THRESHOLD = -80;        // Warn if weaker than this

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
float accelX = 0, accelY = 0, accelZ = 0;
float pitch = 0, roll = 0;
float totalAccelG = 0;

// Baseline calibration
float baselineX = 0, baselineY = 0, baselineZ = 0;
float baselineMag = 1.0;  // Baseline magnitude (~1g when flat)
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
unsigned long lastPostureReport = 0;

// Impact buffer (stores DELTA values, not raw)
float impactBuffer[10];
int bufferIndex = 0;

// NTP Time
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 19800;  // IST = UTC+5:30
const int daylightOffset_sec = 0;

// Forward declarations
void readMPU6050();
void calculateOrientation();
float calculateDeltaG();

// ════════════════════════════════════════════════════════════════════
// ▶ SETUP
// ════════════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    delay(500);
    
    printBanner();
    
    // Initialize LED (off by default to save power)
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    blinkLED(2, 100);  // Startup indication
    
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
    Serial.println("⏰ NTP time sync requested");
    
    // Skip TLS verification for prototype (document in README)
    // Production: use certificate pinning
    wifiClient.setInsecure();
    
    // Calibrate accelerometer at rest position
    calibrateAccelerometer();
    
    // PREFILL impact buffer with current delta readings
    Serial.println("📊 Prefilling impact buffer...");
    for (int i = 0; i < 10; i++) {
        readMPU6050();
        impactBuffer[i] = fabs(totalAccelG - baselineMag);
        delay(20);
    }
    
    Serial.println("\n═══════════════════════════════════════");
    Serial.println("✅ SafeStep Ready - Monitoring for falls");
    Serial.println("═══════════════════════════════════════\n");
    
    blinkLED(2, 150);
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
    
    // Calculate DELTA from baseline (key improvement!)
    float deltaG = calculateDeltaG();
    
    // Update impact buffer with DELTA value (not raw totalAccelG)
    impactBuffer[bufferIndex] = deltaG;
    bufferIndex = (bufferIndex + 1) % 10;
    
    // State machine for fall detection
    switch (currentState) {
        case STATE_IDLE:
            handleIdleState(deltaG);
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
    
    #if SEND_POSTURE
    // Optional posture reporting
    if (millis() - lastPostureReport > (POSTURE_INTERVAL_SEC * 1000UL)) {
        sendPostureSnapshot();
        lastPostureReport = millis();
    }
    #endif
    
    // Small delay for stability (~50Hz sampling)
    delay(20);
}

// ════════════════════════════════════════════════════════════════════
// ▶ DELTA CALCULATION (Key improvement!)
// ════════════════════════════════════════════════════════════════════
float calculateDeltaG() {
    // Delta = difference from baseline magnitude
    // This accounts for sensor drift and orientation
    return fabs(totalAccelG - baselineMag);
}

// ════════════════════════════════════════════════════════════════════
// ▶ STATE HANDLERS
// ════════════════════════════════════════════════════════════════════

void handleIdleState(float deltaG) {
    // Use DELTA-based detection (not raw threshold)
    if (deltaG > IMPACT_THRESHOLD_G) {
        Serial.println("\n⚠️ HIGH IMPACT DETECTED!");
        Serial.print("   Delta G: ");
        Serial.print(deltaG, 2);
        Serial.print("g (threshold: ");
        Serial.print(IMPACT_THRESHOLD_G, 1);
        Serial.println("g)");
        
        impactDetectedTime = millis();
        currentState = STATE_IMPACT_DETECTED;
        blinkLED(1, 80);
    }
    
    // Check for freefall (sudden weightlessness)
    if (totalAccelG < FREEFALL_THRESHOLD_G) {
        Serial.println("\n⚠️ FREEFALL DETECTED!");
        impactDetectedTime = millis();
        currentState = STATE_IMPACT_DETECTED;
        blinkLED(1, 80);
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
        bool significantAngleChange = (fabs(pitch) > ORIENTATION_CHANGE_DEG || 
                                        fabs(roll) > ORIENTATION_CHANGE_DEG);
        
        if (recentMovement < POST_IMPACT_STILLNESS_G && significantAngleChange) {
            Serial.println("\n🚨 FALL CONFIRMED!");
            Serial.print("   Stillness: ");
            Serial.print(recentMovement, 3);
            Serial.print("g (threshold: ");
            Serial.print(POST_IMPACT_STILLNESS_G, 2);
            Serial.println("g)");
            currentState = STATE_FALL_CONFIRMED;
        } else {
            Serial.println("✅ False alarm - person recovered");
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
    
    digitalWrite(LED_PIN, HIGH);
    
    // Send alert with retry
    bool success = sendFallAlert(peakImpact, pitch, roll);
    
    if (success) {
        Serial.println("\n✅ Alert sent successfully!");
        blinkLED(3, 200);
    } else {
        Serial.println("\n❌ Alert failed to send!");
        blinkLED(8, 50);
    }
    
    digitalWrite(LED_PIN, LOW);
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
    
    bool badPosture = (fabs(pitch) > POSTURE_BAD_PITCH || fabs(roll) > POSTURE_BAD_ROLL);
    
    if (badPosture) {
        if (badPostureStartTime == 0) {
            badPostureStartTime = now;
        } else if (now - badPostureStartTime > POSTURE_ALERT_DELAY_MS) {
            Serial.println("⚠️ Sustained poor posture detected");
            badPostureStartTime = now;  // Reset to avoid spam
        }
    } else {
        badPostureStartTime = 0;  // Reset if posture is good
    }
}

#if SEND_POSTURE
// Optional: Send posture snapshot to worker (B2 implementation)
void sendPostureSnapshot() {
    if (WiFi.status() != WL_CONNECTED) return;
    
    String postureState = "GOOD";
    if (fabs(pitch) > POSTURE_BAD_PITCH || fabs(roll) > POSTURE_BAD_ROLL) {
        postureState = "POOR";
    } else if (fabs(pitch) > POSTURE_BAD_PITCH * 0.6 || fabs(roll) > POSTURE_BAD_ROLL * 0.6) {
        postureState = "WARNING";
    }
    
    StaticJsonDocument<256> doc;
    doc["device_id"] = DEVICE_ID;
    doc["event_type"] = "POSTURE_UPDATE";
    doc["timestamp"] = getISOTimestamp();
    doc["pitch"] = String(pitch, 1);
    doc["roll"] = String(roll, 1);
    doc["posture_state"] = postureState;
    
    String payload;
    serializeJson(doc, payload);
    
    Serial.println("📊 Sending posture: " + payload);
    
    http.begin(wifiClient, String(WORKER_URL) + "/writePosture");
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(5000);
    
    int code = http.POST(payload);
    if (code > 0) {
        Serial.printf("Posture sent: HTTP %d\n", code);
    }
    http.end();
}
#endif

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
    
    // Calculate baseline magnitude (should be ~1g when flat)
    baselineMag = sqrt(baselineX*baselineX + baselineY*baselineY + baselineZ*baselineZ);
    
    Serial.print("   Baseline: X=");
    Serial.print(baselineX, 3);
    Serial.print("g, Y=");
    Serial.print(baselineY, 3);
    Serial.print("g, Z=");
    Serial.print(baselineZ, 3);
    Serial.print("g  Mag=");
    Serial.print(baselineMag, 3);
    Serial.println("g");
    
    isCalibrated = true;
}

// ════════════════════════════════════════════════════════════════════
// ▶ ANALYSIS HELPER FUNCTIONS
// ════════════════════════════════════════════════════════════════════

float calculateRecentMovement() {
    // Calculate standard deviation of recent delta readings
    float sum = 0;
    for (int i = 0; i < 10; i++) {
        sum += impactBuffer[i];
    }
    float avg = sum / 10.0;
    
    float variance = 0;
    for (int i = 0; i < 10; i++) {
        variance += (impactBuffer[i] - avg) * (impactBuffer[i] - avg);
    }
    
    return sqrt(variance / 10.0);
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
    while (WiFi.status() != WL_CONNECTED && attempts < 40) {
        delay(300);
        Serial.print(".");
        attempts++;
    }
    Serial.println();
    
    if (WiFi.status() == WL_CONNECTED) {
        int rssi = WiFi.RSSI();
        Serial.println("✅ WiFi connected!");
        Serial.print("   IP Address: ");
        Serial.println(WiFi.localIP());
        Serial.print("   Signal Strength: ");
        Serial.print(rssi);
        Serial.print(" dBm");
        
        // RSSI warning
        if (rssi < RSSI_WARNING_THRESHOLD) {
            Serial.println(" ⚠️ WEAK SIGNAL!");
        } else {
            Serial.println(" (good)");
        }
    } else {
        Serial.println("❌ WiFi connection FAILED!");
    }
}

bool sendFallAlert(float impactG, float currentPitch, float currentRoll) {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("❌ WiFi not connected!");
        return false;
    }
    
    // RSSI warning
    int rssi = WiFi.RSSI();
    if (rssi < RSSI_WARNING_THRESHOLD) {
        Serial.print("⚠️ Weak WiFi signal: ");
        Serial.print(rssi);
        Serial.println(" dBm - may fail");
    }
    
    // Validate FCM token (length check - tokens are typically 150+ chars)
    if (strlen(FCM_TOKEN) < 100) {
        Serial.println("❌ ERROR: FCM_TOKEN appears invalid (too short)!");
        return false;
    }
    
    // Check for placeholder
    if (String(FCM_TOKEN).indexOf("YOUR_FCM") >= 0) {
        Serial.println("❌ ERROR: FCM_TOKEN not configured!");
        return false;
    }
    
    Serial.println("\n📤 Sending alert to Cloudflare Worker...");
    
    // Build JSON payload matching FCM contract
    StaticJsonDocument<768> doc;
    doc["token"] = FCM_TOKEN;
    doc["device_id"] = DEVICE_ID;
    doc["event_type"] = "FALL_CONFIRMED";
    doc["event_id"] = String("evt_") + String(millis());
    doc["timestamp"] = getISOTimestamp();
    doc["impact_g"] = String(impactG, 2);
    doc["pitch"] = String(currentPitch, 1);
    doc["roll"] = String(currentRoll, 1);
    doc["firmware_version"] = FIRMWARE_VERSION;
    
    String payload;
    serializeJson(doc, payload);
    
    Serial.println("   Payload: " + payload);
    
    // RETRY LOOP
    bool success = false;
    for (int attempt = 1; attempt <= HTTP_RETRY_COUNT && !success; attempt++) {
        Serial.printf("   Attempt %d/%d...\n", attempt, HTTP_RETRY_COUNT);
        
        http.begin(wifiClient, WORKER_URL);
        http.addHeader("Content-Type", "application/json");
        http.setTimeout(15000);
        
        int httpCode = http.POST(payload);
        
        if (httpCode > 0) {
            Serial.print("   HTTP Response: ");
            Serial.println(httpCode);
            
            String response = http.getString();
            Serial.println("   Response: " + response);
            
            // Accept 200, 201, 202 as success
            success = (httpCode == 200 || httpCode == 201 || httpCode == 202);
        } else {
            Serial.print("   HTTP Error: ");
            Serial.println(http.errorToString(httpCode));
        }
        
        http.end();
        
        if (!success && attempt < HTTP_RETRY_COUNT) {
            Serial.println("   Retrying in 500ms...");
            delay(500);
        }
    }
    
    return success;
}

String getISOTimestamp() {
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo, 1000)) {
        // Fallback if NTP not synced
        char buf[40];
        snprintf(buf, sizeof(buf), "unsynced-%lu", millis() / 1000);
        return String(buf);
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
    Serial.println("║      ███████╗ █████╗ ███████╗███████╗███████╗████████╗       ║");
    Serial.println("║      ██╔════╝██╔══██╗██╔════╝██╔════╝██╔════╝╚══██╔══╝       ║");
    Serial.println("║      ███████╗███████║█████╗  █████╗  ███████╗   ██║          ║");
    Serial.println("║      ╚════██║██╔══██║██╔══╝  ██╔══╝  ╚════██║   ██║          ║");
    Serial.println("║      ███████║██║  ██║██║     ███████╗███████║   ██║          ║");
    Serial.println("║      ╚══════╝╚═╝  ╚═╝╚═╝     ╚══════╝╚══════╝   ╚═╝          ║");
    Serial.println("║                                                              ║");
    Serial.println("║           Fall Detection Wearable - v2.1.0                   ║");
    Serial.println("║         ESP32 + MPU6050 Production (FIXED)                   ║");
    Serial.println("╚══════════════════════════════════════════════════════════════╝");
    Serial.println();
    Serial.print("Device ID: ");
    Serial.println(DEVICE_ID);
    Serial.print("Firmware:  ");
    Serial.println(FIRMWARE_VERSION);
    Serial.println();
}
