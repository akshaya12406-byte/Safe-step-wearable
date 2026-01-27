# SafeStep - Fall Detection Wearable App

> Competition-ready Android app for the SafeStep fall detection wearable system.   
> **LEAP Competition Entry**

[![Android](https://img.shields.io/badge/Platform-Android%2026+-green.svg)](https://developer.android.com)
[![Firebase](https://img.shields.io/badge/Firebase-Spark%20(Free)-orange.svg)](https://firebase.google.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)

## Architecture Overview

```
┌──────────────┐     HTTPS      ┌────────────────────┐     FCM v1     ┌────────────────┐
│   ESP32 +    │ ──────────────>│  Cloudflare Worker │ ──────────────>│  Android App   │
│   MPU6050    │                │   (FCM Relay)      │                │  (Caregiver)   │
└──────┬───────┘                └────────────────────┘                └────────┬───────┘
       │                                                                       │
       │  Firestore REST API                                                   │
       │                                                                       │
       └───────────────────────────> Firestore <───────────────────────────────┘
                                  (events, posture)
```

### Key Design Decisions

| Component | Choice | Rationale |
|-----------|--------|-----------|
| FCM API | HTTP v1 via Cloudflare Worker | Legacy FCM is deprecated; HTTP v1 requires OAuth |
| Relay | Cloudflare Worker | Free tier, no cold starts, handles OAuth tokens |
| Fall Detection | ESP32 ONLY | Resource-constrained device optimized for motion |
| Posture Detection | ESP32 → Firestore | App displays, never calculates |
| Android App | Display + Actions ONLY | No motion analysis |

---

## Features

### 📱 Full-Screen Alert
- Wakes device even when locked
- High-priority notification with full-screen intent
- Large buttons (72dp+) for elderly accessibility
- Demo Mode prevents real calls during testing

### 📊 Professional Dashboard
- **Current Posture** – Real-time from Firestore (ESP32-written)
- **Device Status** – Online/Offline indicator, battery, last seen
- **Last Fall Summary** – Quick access to most recent event
- **Recent Events** – Scrollable event history

### ⚙️ Settings
- Emergency contact number
- Demo Mode toggle (default OFF)
- Auto-call toggle with consent dialog (default OFF)
- Developer Mode (7-tap + PIN)

---

## Quick Start

### Prerequisites

- Android Studio Arctic Fox+
- JDK 11+
- Firebase project (Spark plan)
- Cloudflare Worker deployed (see below)

### 1. Clone & Setup

```bash
git clone https://github.com/your-org/Safe-step-wearable.git
cd Safe-step-wearable
```

### 2. Firebase Configuration

1. Create Firebase project at [firebase.google.com](https://console.firebase.google.com)
2. Add Android app: `com.safestep.app`
3. Download `google-services.json` → place in `app/`
4. Enable **Cloud Messaging** (FCM) and **Firestore**

### 3. Cloudflare Worker (FCM Relay)

The Cloudflare Worker handles OAuth for FCM HTTP v1 API.

**Worker is already deployed.** ESP32 sends to:
```
https://safestep-fcm.your-subdomain.workers.dev/send
```

If you need to deploy your own:

```javascript
// workers/fcm-relay.js (example structure)
export default {
  async fetch(request, env) {
    const { device_id, event_type, impact_g } = await request.json();
    
    // Get OAuth token from service account (stored in env)
    const accessToken = await getAccessToken(env.SERVICE_ACCOUNT_KEY);
    
    // Send to FCM HTTP v1
    const fcmResponse = await fetch(
      `https://fcm.googleapis.com/v1/projects/${env.PROJECT_ID}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          message: {
            topic: 'caregiver',
            data: { device_id, event_type, impact_g }
          }
        })
      }
    );
    
    return new Response('OK');
  }
};
```

### 4. Build & Run

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Firestore Schema

```
devices/{device_id}/
├── meta/info
│   ├── last_seen: Timestamp
│   ├── battery_pct: Number
│   ├── fw_version: String
│   └── fcm_token: String
├── posture/latest          ← ESP32 writes this
│   ├── state: "GOOD" | "BAD"
│   ├── duration_seconds: Number
│   ├── last_updated: Timestamp
│   ├── pitch: Number
│   └── roll: Number
└── events/{event_id}       ← ESP32 writes, App reads
    ├── event_type: "FALL_CONFIRMED"
    ├── timestamp: String (ISO)
    ├── impact_g: Number
    ├── handled: Boolean
    └── acknowledged_by: String
```

---

## FCM Message Format (Data Payload)

ESP32 → Cloudflare Worker sends:
```json
{
  "device_id": "ESP32_01",
  "event_type": "FALL_CONFIRMED",
  "event_id": "evt_12345",
  "timestamp": "2026-01-28T15:50:00Z",
  "impact_g": "3.05"
}
```

Worker forwards to FCM HTTP v1:
```json
{
  "message": {
    "topic": "caregiver",
    "android": {
      "priority": "high"
    },
    "data": {
      "event_type": "FALL_CONFIRMED",
      "device_id": "ESP32_01",
      "event_id": "evt_12345",
      "timestamp": "2026-01-28T15:50:00Z",
      "impact_g": "3.05"
    }
  }
}
```

---

## Testing

### Python Test Harness

```bash
cd tools
pip install requests

# For direct FCM testing (requires Cloudflare Worker URL)
python test_fire_event.py --fcm
```

### In-App Testing

1. Settings → enable **Demo Mode**
2. Tap version 7× → enter PIN `1234`
3. Developer Mode → **Simulate Event**
4. Full-screen AlertActivity appears

---

## Security

### ✅ Production-Ready Design

| Concern | Mitigation |
|---------|------------|
| FCM Server Key exposure | Never embedded in app or ESP32 |
| OAuth token management | Handled by Cloudflare Worker |
| FCM topic security | Only server can send to topics |
| No secrets in APK | Only Firebase project ID (public) |

### ⚠️ Prototype Trade-offs

For hackathon, the Cloudflare Worker URL is hardcoded in ESP32 firmware. In production:
- ESP32 would have a device certificate
- Worker would validate device identity
- Mutual TLS recommended

---

## Project Structure

```
Safe-step-wearable/
├── app/
│   ├── src/main/java/com/safestep/app/
│   │   ├── ui/           # Fragments, Activities
│   │   ├── service/      # SafeStepFirebaseService
│   │   ├── data/         # Repositories (Device, Event, Posture)
│   │   └── model/        # Data classes
│   └── src/main/res/     # Layouts, strings, themes
├── hardware/
│   └── esp32/            # Arduino sample code
├── tools/
│   └── test_fire_event.py
├── README.md
└── ACCEPTANCE_CHECKLIST.md
```

---

## Demo Script (60-90 seconds)

```
[INTRO - 15s]
"SafeStep is an emergency alert system for elderly users wearing fall 
detection devices. When the ESP32 wearable detects a fall, caregivers 
receive an instant alert—even when their phone is locked."

[DASHBOARD - 15s]
1. Show dashboard with posture status
2. "The ESP32 monitors posture and writes to Firestore"
3. "The app displays real-time updates—no polling"

[ALERT DEMO - 30s]
1. Trigger fall alert (via Worker or Simulate button)
2. Show full-screen alert appearing
3. "Alert wakes the device and shows over lock screen"
4. "Large buttons for accessibility"
5. Show Demo Mode preventing actual call

[ARCHITECTURE - 20s]
"The ESP32 sends alerts through our Cloudflare Worker relay to FCM 
HTTP v1. This is the modern, production-grade approach—Legacy FCM 
is deprecated. The entire system runs on Firebase's free Spark plan."

[CLOSE - 10s]
"SafeStep shows how life-saving IoT systems can be built with 
affordable hardware and free cloud services."
```

---

## Team

Built for LEAP Competition by the SafeStep Team.

---

## License

MIT License
