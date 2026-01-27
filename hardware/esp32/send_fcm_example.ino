/**
 * SafeStep ESP32 - Direct FCM Send Example
 * 
 * This sketch demonstrates how to send FCM messages directly from ESP32 
 * using the FCM Legacy HTTP API (works with Firebase Spark plan).
 * 
 * HARDWARE REQUIREMENTS:
 * - ESP32 (any variant with WiFi)
 * - MPU6050 accelerometer (for fall detection - not shown here)
 * 
 * SECURITY WARNING:
 * Embedding the FCM server key in firmware is NOT secure for production.
 * For hackathon/prototype only. See README for production migration path.
 * 
 * SETUP:
 * 1. Replace WIFI_SSID and WIFI_PASSWORD with your network credentials
 * 2. Replace FCM_SERVER_KEY with your Firebase Cloud Messaging server key
 *    (Firebase Console → Project Settings → Cloud Messaging → Server Key)
 * 3. Replace FCM_TOPIC or DEVICE_TOKEN as needed
 * 4. Upload to ESP32
 * 
 * Author: SafeStep Team
 * License: MIT
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

// ============== CONFIGURATION ==============
// WiFi credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Firebase Cloud Messaging
// Get from: Firebase Console → Project Settings → Cloud Messaging → Server Key
const char* FCM_SERVER_KEY = "YOUR_FCM_SERVER_KEY_HERE";

// Send to topic (recommended) or specific device token
const bool USE_TOPIC = true;
const char* FCM_TOPIC = "caregiver";
const char* DEVICE_TOKEN = "YOUR_DEVICE_TOKEN_HERE";  // Only if USE_TOPIC is false

// Device identifier
const char* DEVICE_ID = "ESP32_01";
const char* FIRMWARE_VERSION = "1.0.0";

// FCM endpoint
const char* FCM_URL = "https://fcm.googleapis.com/fcm/send";
// ==========================================

WiFiClientSecure wifiClient;
HTTPClient http;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=== SafeStep ESP32 FCM Sender ===");
  
  // Connect to WiFi
  connectWiFi();
  
  // Skip TLS verification for simplicity (not recommended for production)
  wifiClient.setInsecure();
  
  Serial.println("Ready to send FCM messages.");
  Serial.println("Call sendFallAlert() to test.");
  
  // Test: Send a fall alert after 5 seconds
  delay(5000);
  sendFallAlert(3.05, 12.4, 5.1);
}

void loop() {
  // In real application:
  // 1. Read MPU6050 accelerometer data
  // 2. Run fall detection algorithm
  // 3. If fall detected, call sendFallAlert()
  
  delay(1000);
}

/**
 * Connect to WiFi network
 */
void connectWiFi() {
  Serial.print("Connecting to WiFi: ");
  Serial.println(WIFI_SSID);
  
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi connected!");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\nWiFi connection FAILED!");
  }
}

/**
 * Send fall alert via FCM
 * 
 * @param impactG Impact force in g
 * @param pitch Pitch angle in degrees
 * @param roll Roll angle in degrees
 */
void sendFallAlert(float impactG, float pitch, float roll) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi not connected!");
    return;
  }
  
  Serial.println("\n📤 Sending FCM fall alert...");
  
  // Build JSON payload
  StaticJsonDocument<512> doc;
  
  // Set destination
  if (USE_TOPIC) {
    String topicPath = String("/topics/") + FCM_TOPIC;
    doc["to"] = topicPath;
  } else {
    doc["to"] = DEVICE_TOKEN;
  }
  
  doc["priority"] = "high";
  
  // Data payload (critical for background delivery)
  JsonObject data = doc.createNestedObject("data");
  data["event_type"] = "FALL_CONFIRMED";
  data["device_id"] = DEVICE_ID;
  data["event_id"] = String("evt_") + String(millis());
  data["timestamp"] = getISOTimestamp();
  data["impact_g"] = String(impactG, 2);
  data["pitch"] = String(pitch, 1);
  data["roll"] = String(roll, 1);
  data["firmware_version"] = FIRMWARE_VERSION;
  
  // Serialize to string
  String payload;
  serializeJson(doc, payload);
  
  Serial.println("Payload:");
  Serial.println(payload);
  
  // Send HTTP POST
  http.begin(wifiClient, FCM_URL);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", String("key=") + FCM_SERVER_KEY);
  
  int httpCode = http.POST(payload);
  
  if (httpCode > 0) {
    Serial.print("HTTP Response: ");
    Serial.println(httpCode);
    
    String response = http.getString();
    Serial.println("Response body:");
    Serial.println(response);
    
    if (httpCode == 200) {
      Serial.println("✅ FCM message sent successfully!");
    } else {
      Serial.println("⚠️ FCM returned non-200 status");
    }
  } else {
    Serial.print("❌ HTTP Error: ");
    Serial.println(http.errorToString(httpCode));
  }
  
  http.end();
}

/**
 * Get current timestamp in ISO format (simplified)
 * Note: For accurate time, use NTP time synchronization
 */
String getISOTimestamp() {
  // Simplified: returns millis-based timestamp
  // In production, use NTP to get real time
  unsigned long ms = millis();
  return String("2026-01-27T") + 
         String((ms / 3600000) % 24) + ":" +
         String((ms / 60000) % 60) + ":" +
         String((ms / 1000) % 60) + "Z";
}

/**
 * Optional: Write event to Firestore REST API
 * 
 * Note: This requires Firebase Auth or a service account token.
 * For prototype, the Android app writes to Firestore when it receives FCM.
 * This function is provided as a reference for direct Firestore writes.
 */
void writeToFirestore(float impactG, float pitch, float roll) {
  // Firestore REST API endpoint:
  // POST https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents/devices/{DEVICE_ID}/events
  //
  // This requires authentication (OAuth2 or Firebase Auth token).
  // For prototype, we let the app write to Firestore when it receives the FCM.
  //
  // If you need direct ESP32→Firestore writes:
  // 1. Use a Firebase service account
  // 2. Generate a JWT from the service account
  // 3. Send the JWT as Authorization header
  //
  // See: https://firebase.google.com/docs/firestore/use-rest-api
  
  Serial.println("Firestore direct write not implemented in prototype.");
  Serial.println("The Android app writes events when it receives FCM.");
}
