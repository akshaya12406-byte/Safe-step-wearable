# SafeStep - Fall Detection Wearable App

> Competition-ready Android app for the SafeStep fall detection wearable system.

[![Android](https://img.shields.io/badge/Platform-Android%2026+-green.svg)](https://developer.android.com)
[![Firebase](https://img.shields.io/badge/Firebase-Spark%20(Free)-orange.svg)](https://firebase.google.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)

## Overview

SafeStep is an emergency alert system for elderly users wearing ESP32-based fall detection devices. When the wearable detects a fall, it sends an FCM notification that triggers a **full-screen alert** on the caregiver's phone—even when the device is locked.

### Key Features

- 📱 **Full-Screen Alert** — Wakes device and shows over lock screen
- 📞 **Emergency Call** — One-tap call to configured emergency contact  
- 🛡️ **Demo Mode** — Prevents real calls during testing/demos
- 📊 **Dashboard** — Device status, reliability metrics, event history
- 🔧 **Developer Mode** — FCM token display, test tools (7-tap activation)

---

## Quick Start

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 11+
- Firebase project (Spark plan is sufficient)
- Python 3.8+ (for test harness)

### 1. Clone & Setup

```bash
git clone https://github.com/your-org/Safe-step-wearable.git
cd Safe-step-wearable
```

### 2. Firebase Configuration

1. Create a Firebase project at [firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name: `com.safestep.app`
3. Download `google-services.json` and place it in `app/`
4. Enable **Cloud Messaging** (FCM) and **Firestore**

```
app/
├── google-services.json   ← Place here
├── src/
└── build.gradle.kts
```

### 3. Get FCM Server Key

1. Firebase Console → Project Settings → Cloud Messaging
2. Copy the **Server Key** (starts with `AAAA...`)
3. Use this key in:
   - `hardware/esp32/send_fcm_example.ino`
   - `tools/test_fire_event.py`

### 4. Build & Run

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```
Safe-step-wearable/
├── app/                    # Android application
│   ├── src/main/java/com/safestep/app/
│   │   ├── ui/            # Activities & Fragments
│   │   ├── service/       # FCM service
│   │   ├── data/          # Repositories
│   │   └── model/         # Data classes
│   └── src/main/res/      # Layouts, strings, themes
├── hardware/               
│   └── esp32/             # ESP32 sample code
├── tools/                  # Test harness scripts
├── test_harness/           # Node.js FCM sender
└── docs/                   # Additional documentation
```

---

## Configuration

### Settings (In-App)

| Setting | Default | Description |
|---------|---------|-------------|
| Emergency Number | `911` | Number to call on fall alert |
| Demo Mode | OFF | When ON, shows toast instead of calling |
| Auto-Call | OFF | Directly calls without opening dialer |

### Developer Mode

Access via: **Settings → tap version 7 times → enter PIN `1234`**

Features:
- View/copy FCM token
- Simulate fall event
- Test notification trigger

---

## FCM Message Format

ESP32 or test harness should send this payload:

```json
{
  "to": "/topics/caregiver",
  "priority": "high",
  "data": {
    "event_type": "FALL_CONFIRMED",
    "device_id": "ESP32_01",
    "event_id": "evt_1234567890",
    "timestamp": "2026-01-27T15:50:00Z",
    "impact_g": "3.05",
    "pitch": "12.4",
    "roll": "5.1"
  }
}
```

---

## Testing

### Using Python Test Harness

```bash
cd tools
pip install requests

# Edit FCM_SERVER_KEY in test_fire_event.py

python test_fire_event.py --fcm
# Check your Android device for full-screen alert!
```

### Using Node.js Test Harness

```bash
cd test_harness
npm install

# Edit server key in send_fcm.js

node send_fcm.js
```

### Manual Testing

1. Open app → Settings → enable Demo Mode
2. Go to Developer Mode (7-tap version + PIN 1234)
3. Tap "Simulate Event"
4. Full-screen AlertActivity should appear

---

## Security Notes

> ⚠️ **PROTOTYPE SECURITY**

This prototype embeds the FCM server key in the ESP32 firmware for simplicity. This is **NOT secure for production**.

### Production Migration Path

1. **Deploy a Relay Server** (Cloudflare Worker, Render, or Heroku free tier)
2. ESP32 sends signed request to relay server
3. Relay validates signature and forwards to FCM
4. FCM server key **never leaves the server**

Example relay (Node.js/Express):
```javascript
app.post('/api/fall-alert', async (req, res) => {
  const { deviceId, signature, data } = req.body;
  if (!verifySignature(deviceId, signature)) {
    return res.status(401).send('Invalid signature');
  }
  await sendFCM(process.env.FCM_SERVER_KEY, data);
  res.send('OK');
});
```

---

## OEM Battery Optimization

Some Android OEMs aggressively kill background apps. For reliable FCM delivery:

| OEM | Setting |
|-----|---------|
| Xiaomi | Settings → Apps → SafeStep → Autostart: ON |
| Huawei | Settings → Battery → App Launch → SafeStep → Manage manually: ON |
| Samsung | Settings → Apps → SafeStep → Battery → Allow background activity |
| OnePlus | Settings → Battery → Battery optimization → SafeStep → Don't optimize |

---

## Firestore Schema

```
devices/{device_id}/
├── meta/info
│   ├── last_seen: Timestamp
│   ├── battery_pct: Number
│   ├── fw_version: String
│   └── fcm_token: String
└── events/{event_id}
    ├── event_type: String ("FALL_CONFIRMED")
    ├── timestamp: String (ISO format)
    ├── impact_g: Number
    ├── pitch: Number
    ├── roll: Number
    ├── handled: Boolean
    └── acknowledged_by: String
```

---

## Demo Script (60-90 seconds)

```
[INTRODUCTION - 15s]
"SafeStep is an emergency alert system for elderly users with wearable 
fall detectors. When a fall is detected, caregivers receive an instant 
alert—even when their phone is locked."

[DEMO - 45s]
1. Show the ESP32 wearable device
2. "The MPU6050 sensor continuously monitors for falls"
3. Trigger a simulated fall (or use test harness)
4. Show full-screen alert appearing
5. "The alert wakes the device and shows over the lock screen"
6. "One tap to call emergency services"
7. Show Demo Mode preventing actual call

[ARCHITECTURE - 20s]
"The ESP32 sends directly to Firebase Cloud Messaging. The app uses 
Firestore for event history. All on Firebase's free Spark plan—no 
server required for the prototype."

[CLOSE - 10s]
"SafeStep demonstrates how life-saving alerts can be built with 
affordable hardware and free cloud services."
```

---

## License

MIT License - See [LICENSE](LICENSE) for details.

---

## Team

Built for LEAP Competition by the SafeStep Team.
