/**
 * SafeStep ESP32 - Cloudflare Worker Integration
 * 
 * This sketch demonstrates how to send fall alerts from ESP32 
 * to the SafeStep Android app via Cloudflare Worker relay.
 * 
 * ARCHITECTURE:
 * ESP32 → HTTPS → Cloudflare Worker → FCM HTTP v1 → Android App
 * 
 * SETUP:
 * 1. Replace WIFI_SSID and WIFI_PASSWORD with your network credentials
 * 2. Replace WORKER_URL with your Cloudflare Worker URL
 * 3. Upload to ESP32
 * 
 * Author: SafeStep Team
 * License: MIT
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

// ============== CONFIGURATION ==============
// WiFi credentials - UPDATE THESE
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Cloudflare Worker URL - PASTE YOUR WORKER URL HERE
// Example: https://safestep-fcm.john-doe.workers.dev
const char* WORKER_URL = "https://safestep-fcm.YOUR-SUBDOMAIN.workers.dev";

// Device identifier (must match Firestore path)
const char* DEVICE_ID = "ESP32_01";
const char* FIRMWARE_VERSION = "1.0.0";
// ==========================================

WiFiClientSecure wifiClient;
HTTPClient http;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n");
  Serial.println("╔══════════════════════════════════════╗");
  Serial.println("║     SafeStep ESP32 FCM Sender        ║");
  Serial.println("║   Cloudflare Worker Integration      ║");
  Serial.println("╚══════════════════════════════════════╝");
  
  // Connect to WiFi
  connectWiFi();
  
  // Skip TLS certificate verification for simplicity
  // In production, you should use proper certificate pinning
  wifiClient.setInsecure();
  
  Serial.println("\n✅ Ready to send fall alerts!");
  Serial.println("📍 Will send test alert in 5 seconds...\n");
  
  // Test: Send a fall alert after 5 seconds
  delay(5000);
  
  Serial.println("🚨 Sending test fall alert...");
  bool success = sendFallAlert(3.05, 12.4, 5.1);
  
  if (success) {
    Serial.println("\n✅ Test complete! Check your Android device.");
  } else {
    Serial.println("\n❌ Test failed. Check configuration.");
  }
}

void loop() {
  // In a real application:
  // 1. Read MPU6050 accelerometer data
  // 2. Run fall detection algorithm
  // 3. If fall detected, call sendFallAlert()
  
  // Example: Simulate fall detection every 30 seconds for testing
  // delay(30000);
  // sendFallAlert(2.5, 10.0, 8.0);
  
  delay(1000);
}

/**
 * Connect to WiFi network
 */
void connectWiFi() {
  Serial.print("📶 Connecting to WiFi: ");
  Serial.println(WIFI_SSID);
  
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  Serial.println();
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("✅ WiFi connected!");
    Serial.print("   IP Address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("❌ WiFi connection FAILED!");
    Serial.println("   Check SSID and password");
  }
}

/**
 * Send fall alert via Cloudflare Worker
 * 
 * @param impactG Impact force in g
 * @param pitch Pitch angle in degrees
 * @param roll Roll angle in degrees
 * @return true if successful, false otherwise
 */
bool sendFallAlert(float impactG, float pitch, float roll) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("❌ WiFi not connected!");
    return false;
  }
  
  // Check if Worker URL is configured
  if (String(WORKER_URL).indexOf("YOUR-SUBDOMAIN") >= 0) {
    Serial.println("❌ ERROR: WORKER_URL not configured!");
    Serial.println("   Please update WORKER_URL in the code");
    return false;
  }
  
  Serial.println("\n📤 Sending fall alert to Cloudflare Worker...");
  Serial.print("   URL: ");
  Serial.println(WORKER_URL);
  
  // Build JSON payload
  StaticJsonDocument<512> doc;
  doc["device_id"] = DEVICE_ID;
  doc["event_type"] = "FALL_CONFIRMED";
  doc["event_id"] = String("evt_") + String(millis());
  doc["timestamp"] = getISOTimestamp();
  doc["impact_g"] = impactG;
  doc["pitch"] = pitch;
  doc["roll"] = roll;
  doc["firmware_version"] = FIRMWARE_VERSION;
  
  // Serialize to string
  String payload;
  serializeJson(doc, payload);
  
  Serial.println("   Payload:");
  Serial.println("   " + payload);
  
  // Send HTTP POST to Cloudflare Worker
  http.begin(wifiClient, WORKER_URL);
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(10000); // 10 second timeout
  
  int httpCode = http.POST(payload);
  
  bool success = false;
  
  if (httpCode > 0) {
    Serial.print("\n   HTTP Response Code: ");
    Serial.println(httpCode);
    
    String response = http.getString();
    Serial.print("   Response: ");
    Serial.println(response);
    
    if (httpCode == 200) {
      Serial.println("\n✅ FCM alert sent successfully!");
      success = true;
    } else {
      Serial.println("\n⚠️ Worker returned non-200 status");
    }
  } else {
    Serial.print("\n❌ HTTP Error: ");
    Serial.println(http.errorToString(httpCode));
    
    if (httpCode == -1) {
      Serial.println("   Possible causes:");
      Serial.println("   - Worker URL is incorrect");
      Serial.println("   - Network connectivity issue");
      Serial.println("   - SSL/TLS handshake failed");
    }
  }
  
  http.end();
  return success;
}

/**
 * Get current timestamp in ISO format
 * Note: For accurate time, use NTP synchronization
 */
String getISOTimestamp() {
  // For prototype, we use a placeholder timestamp
  // The server will use its own timestamp anyway
  return "2026-01-28T00:00:00Z";
}

/**
 * Write posture data to Firestore
 * This is done separately via Firestore REST API
 * 
 * For posture updates, ESP32 writes directly to Firestore:
 * devices/{device_id}/posture/latest
 */
void updatePosture(String state, int durationSeconds, float pitch, float roll) {
  // Posture updates go to Firestore, not FCM
  // This would use Firestore REST API with authentication
  // For prototype, the Android app can simulate this data
  
  Serial.print("Posture update: ");
  Serial.print(state);
  Serial.print(" for ");
  Serial.print(durationSeconds);
  Serial.println(" seconds");
}
