# SafeStep Complete Setup Guide

This guide covers everything you need to set up SafeStep from scratch.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SETUP CHECKLIST                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ [ ] 1. Firebase Project Setup                                               │
│ [ ] 2. Cloudflare Worker Deployment                                         │
│ [ ] 3. Android App Configuration                                            │
│ [ ] 4. ESP32 Configuration                                                  │
│ [ ] 5. Firestore Rules                                                      │
│ [ ] 6. Test the Flow                                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Firebase Project Setup

### 1.1 Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Click **Add Project** → Name it `safestep-leap`
3. Disable Google Analytics (optional for prototype)
4. Click **Create Project**

### 1.2 Add Android App

1. In Firebase Console → click **Android** icon
2. Package name: `com.safestep.app`
3. Register app
4. Download `google-services.json`
5. Place it in: `Safe-step-wearable/app/google-services.json`

### 1.3 Enable Firestore

1. Firebase Console → **Build** → **Firestore Database**
2. Click **Create database**
3. Choose **Start in test mode** (for prototype)
4. Select region closest to you

### 1.4 Get Service Account for Cloudflare Worker

1. Firebase Console → ⚙️ **Project Settings** → **Service accounts**
2. Click **Generate new private key**
3. Download the JSON file (keep it safe!)
4. This file will be used in Cloudflare Worker

---

## Step 2: Cloudflare Worker Setup

### 2.1 Create Cloudflare Account

1. Go to [Cloudflare Workers](https://workers.cloudflare.com)
2. Sign up for free
3. Go to **Workers & Pages**

### 2.2 Create the Worker

1. Click **Create Application** → **Create Worker**
2. Name it: `safestep-fcm`
3. Click **Deploy** (with default code)
4. Click **Edit Code**
5. Replace ALL code with this:

```javascript
/**
 * SafeStep FCM Relay Worker
 * 
 * Receives fall alerts from ESP32 and forwards to Android via FCM HTTP v1
 * Handles OAuth token generation from service account
 */

// Helper to get OAuth access token from service account
async function getAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const exp = now + 3600;
  
  const header = { alg: 'RS256', typ: 'JWT' };
  const claim = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: exp
  };
  
  // Helper functions for JWT
  function base64url(source) {
    let encodedSource = btoa(String.fromCharCode(...new Uint8Array(source)));
    encodedSource = encodedSource.replace(/=+$/, '');
    encodedSource = encodedSource.replace(/\+/g, '-');
    encodedSource = encodedSource.replace(/\//g, '_');
    return encodedSource;
  }
  
  function strToArrayBuffer(str) {
    const buf = new ArrayBuffer(str.length);
    const bufView = new Uint8Array(buf);
    for (let i = 0; i < str.length; i++) {
      bufView[i] = str.charCodeAt(i);
    }
    return buf;
  }
  
  // Encode header and claims
  const encodedHeader = btoa(JSON.stringify(header)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const encodedClaim = btoa(JSON.stringify(claim)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const signatureInput = `${encodedHeader}.${encodedClaim}`;
  
  // Import private key
  const pemContents = serviceAccount.private_key
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\n/g, '');
  const binaryKey = Uint8Array.from(atob(pemContents), c => c.charCodeAt(0));
  
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    binaryKey,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  
  // Sign
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    strToArrayBuffer(signatureInput)
  );
  
  const encodedSignature = base64url(signature);
  const jwt = `${signatureInput}.${encodedSignature}`;
  
  // Exchange JWT for access token
  const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });
  
  const tokenData = await tokenResponse.json();
  return tokenData.access_token;
}

export default {
  async fetch(request, env) {
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type',
        }
      });
    }
    
    // Only accept POST
    if (request.method !== 'POST') {
      return new Response('Method not allowed', { status: 405 });
    }
    
    try {
      const body = await request.json();
      
      // Validate required fields
      if (!body.device_id || !body.event_type) {
        return new Response('Missing required fields: device_id, event_type', { status: 400 });
      }
      
      // Parse service account from environment
      const serviceAccount = JSON.parse(env.SERVICE_ACCOUNT_JSON);
      
      // Get OAuth token
      const accessToken = await getAccessToken(serviceAccount);
      
      // Build FCM message
      const fcmMessage = {
        message: {
          topic: 'caregiver',
          android: {
            priority: 'high'
          },
          data: {
            event_type: body.event_type,
            device_id: body.device_id,
            event_id: body.event_id || `evt_${Date.now()}`,
            timestamp: body.timestamp || new Date().toISOString(),
            impact_g: String(body.impact_g || '0'),
            pitch: String(body.pitch || '0'),
            roll: String(body.roll || '0')
          }
        }
      };
      
      // Send to FCM HTTP v1
      const fcmResponse = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(fcmMessage)
        }
      );
      
      const fcmResult = await fcmResponse.json();
      
      if (fcmResponse.ok) {
        return new Response(JSON.stringify({ success: true, messageId: fcmResult.name }), {
          headers: { 'Content-Type': 'application/json' }
        });
      } else {
        return new Response(JSON.stringify({ success: false, error: fcmResult }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      
    } catch (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      });
    }
  }
};
```

6. Click **Save and Deploy**

### 2.3 Add Service Account Secret

1. In Cloudflare Dashboard → **Workers & Pages** → your worker → **Settings** → **Variables**
2. Under **Environment Variables**, click **Add variable**
3. Variable name: `SERVICE_ACCOUNT_JSON`
4. Value: **Copy the ENTIRE contents** of your Firebase service account JSON file
5. Click **Encrypt** (important for security!)
6. Click **Save and Deploy**

### 2.4 Get Your Worker URL

Your worker URL will be:
```
https://safestep-fcm.<your-subdomain>.workers.dev
```

**Copy this URL** – you'll need it for ESP32!

---

## Step 3: Android App Configuration

### 3.1 Place Firebase Config

```
Safe-step-wearable/
└── app/
    └── google-services.json  ← Place here
```

### 3.2 Subscribe to FCM Topic

The app automatically subscribes to the `caregiver` topic in `SafeStepApplication.kt`.

If you need to verify, the code is:
```kotlin
FirebaseMessaging.getInstance().subscribeToTopic("caregiver")
```

### 3.3 Build and Install

```bash
cd Safe-step-wearable
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 4: ESP32 Configuration

### 4.1 Where to Paste Worker URL

Open `hardware/esp32/send_fcm_example.ino` and update:

```cpp
// ============== CONFIGURATION ==============
// WiFi credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";           // ← Your WiFi name
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";   // ← Your WiFi password

// Cloudflare Worker URL
const char* WORKER_URL = "https://safestep-fcm.YOUR-SUBDOMAIN.workers.dev";  // ← PASTE YOUR WORKER URL HERE

// Device identifier
const char* DEVICE_ID = "ESP32_01";
// =============================================
```

### 4.2 Updated ESP32 Code (for Cloudflare Worker)

Here's the updated ESP32 code that sends to your Cloudflare Worker:

```cpp
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

// ============== CONFIGURATION ==============
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// PASTE YOUR CLOUDFLARE WORKER URL HERE ↓
const char* WORKER_URL = "https://safestep-fcm.YOUR-SUBDOMAIN.workers.dev";

const char* DEVICE_ID = "ESP32_01";
// =============================================

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=== SafeStep ESP32 ===");
  connectWiFi();
  
  // Test: send fall alert after 5 seconds
  delay(5000);
  sendFallAlert(3.05, 12.4, 5.1);
}

void loop() {
  // Your fall detection code here
  delay(1000);
}

void connectWiFi() {
  Serial.print("Connecting to WiFi...");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println(" Connected!");
}

void sendFallAlert(float impact, float pitch, float roll) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi not connected!");
    return;
  }
  
  WiFiClientSecure client;
  client.setInsecure();  // Skip TLS verification for prototype
  
  HTTPClient http;
  http.begin(client, WORKER_URL);
  http.addHeader("Content-Type", "application/json");
  
  // Build JSON payload
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["event_type"] = "FALL_CONFIRMED";
  doc["event_id"] = String("evt_") + String(millis());
  doc["timestamp"] = "2026-01-28T00:00:00Z";
  doc["impact_g"] = impact;
  doc["pitch"] = pitch;
  doc["roll"] = roll;
  
  String payload;
  serializeJson(doc, payload);
  
  Serial.print("Sending to Worker: ");
  Serial.println(payload);
  
  int httpCode = http.POST(payload);
  
  if (httpCode > 0) {
    Serial.print("Response: ");
    Serial.println(http.getString());
  } else {
    Serial.print("Error: ");
    Serial.println(http.errorToString(httpCode));
  }
  
  http.end();
}
```

---

## Step 5: Firestore Rules (Optional for Production)

For prototype, test mode is fine. For production:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow ESP32 to write events and posture
    match /devices/{deviceId}/{document=**} {
      allow read: if true;  // App can read
      allow write: if true; // ESP32 can write (in production, use auth)
    }
  }
}
```

---

## Step 6: Test the Complete Flow

### Test 1: Python Script (Quick Test)

```bash
cd tools

# Edit test_fire_event.py and change:
# WORKER_URL = "https://safestep-fcm.YOUR-SUBDOMAIN.workers.dev"

python test_fire_event.py --worker
```

### Test 2: In-App Simulation

1. Open SafeStep app
2. Settings → Enable **Demo Mode**
3. Tap version number **7 times** → Enter PIN `1234`
4. Developer Mode → **Simulate Event**
5. Alert should appear!

### Test 3: Full ESP32 Flow

1. Upload code to ESP32
2. Open Serial Monitor
3. Wait 5 seconds
4. Check: Android should show full-screen alert

---

## Quick Reference

| What | Where |
|------|-------|
| Worker URL | ESP32 code: `WORKER_URL` constant |
| Service Account | Cloudflare Worker: `SERVICE_ACCOUNT_JSON` env variable |
| Firebase Config | `app/google-services.json` |
| WiFi Credentials | ESP32 code: `WIFI_SSID`, `WIFI_PASSWORD` |
| Device ID | ESP32 code: `DEVICE_ID` (must match Firestore path) |

---

## Troubleshooting

### "Failed to fetch" from ESP32
- Check WiFi credentials
- Verify Worker URL is correct (https://)
- Check Cloudflare dashboard for errors

### No notification on Android
- Is app subscribed to topic? (Check logs)
- Is google-services.json in place?
- Check Firebase Console → Cloud Messaging for delivery stats

### Worker returns 500
- Check SERVICE_ACCOUNT_JSON is valid JSON
- Ensure it's encrypted in Cloudflare
- Check Cloudflare dashboard → Workers → Logs

---

## Summary

```
1. Create Firebase Project
   └── Download google-services.json
   └── Download Service Account JSON

2. Deploy Cloudflare Worker
   └── Paste worker code
   └── Add SERVICE_ACCOUNT_JSON as encrypted env var
   └── Copy Worker URL

3. Configure Android App
   └── Place google-services.json in app/
   └── Build and install

4. Configure ESP32
   └── Paste Worker URL in code
   └── Set WiFi credentials
   └── Upload to ESP32

5. Test!
```
