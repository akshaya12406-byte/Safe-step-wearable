# SafeStep Project - Complete Context Summary
> **Copy this entire file to Antigravity on any device to resume work**

---

## 🎯 Project Overview

**SafeStep** is a fall detection wearable system for elderly care, built for LEAP Hackathon.

### Architecture
```
┌─────────────────┐      HTTPS POST       ┌─────────────────────┐
│  ESP32 + MPU6050 │ ──────────────────▶ │  Cloudflare Worker  │
│  (Wearable)      │                      │  (v2.1)             │
└─────────────────┘                      └──────────┬──────────┘
                                                     │
                                    ┌────────────────┼────────────────┐
                                    ▼                                 ▼
                           ┌──────────────┐                  ┌──────────────┐
                           │  FCM (Push)   │                  │  Firestore   │
                           └──────┬───────┘                  │  (Database)  │
                                  │                          └──────────────┘
                                  ▼
                           ┌──────────────┐
                           │ Android App  │
                           │ (Compose UI) │
                           └──────────────┘
```

---

## 🔐 Credentials (KEEP PRIVATE!)

```
WiFi SSID:     Harish
WiFi Password: Harish0519
Worker URL:    https://safestep-fcm-relay.harishkumar-sp5511.workers.dev
FCM Token:     fOkz1phjTxy9OUC7YjPBiB:APA91bFTe2KwAqpikownFmm0LqCcG7UoJXTuZVxRWUYLKzDEf8sRXc3OMoNn5-hnpsuGRqu_uBjw8DawHIoqYIeWaM3PxYOCcBL6y5WzlaOpe6VZvKtUMMQ
Device ID:     ESP32_01
Firebase Project: safestep-leap
```

⚠️ **FCM Token changes per device!** Get new token from Developer Mode (7-tap on version).

---

## 📁 Project Structure

```
Safe-step-wearable/
├── app/                              # Android App
│   ├── src/main/java/com/safestep/app/
│   │   ├── ui/
│   │   │   ├── alert/AlertScreen.kt          # Full-screen fall alert
│   │   │   ├── alert/AlertComposeActivity.kt # Lock screen activity
│   │   │   ├── home/HomeScreen.kt            # Dashboard
│   │   │   ├── events/EventHistoryScreen.kt  # Event list
│   │   │   ├── events/EventHistoryFirestoreScreen.kt  # Firestore version
│   │   │   ├── settings/SettingsScreen.kt    # Settings + 7-tap dev mode
│   │   │   ├── developer/DeveloperScreen.kt  # FCM token + test buttons
│   │   │   ├── pairing/PairingScreen.kt      # Device pairing
│   │   │   ├── theme/                        # Material 3 dark theme
│   │   │   └── components/                   # Reusable composables
│   │   ├── service/
│   │   │   └── SafeStepFirebaseService.kt    # FCM handler
│   │   └── data/repository/
│   │       └── FirestoreRepository.kt        # Firestore reads
│   └── google-services.json                  # Firebase config
│
├── hardware/esp32/
│   ├── safestep_FINAL.ino          # ✅ READY TO UPLOAD - has credentials
│   ├── safestep_fall_detector_v2.1.ino  # Generic version
│   └── README.md                    # Wiring guide
│
├── cloudflare/
│   ├── worker_v2.1.js              # ✅ DEPLOYED - FCM + Firestore writes
│   ├── worker_v2.js                # Previous version
│   └── worker.js                   # Original version
│
└── docs/, test_harness/, tools/    # Utilities
```

---

## 🔧 Component Details

### 1. ESP32 Firmware (safestep_FINAL.ino)

**Features:**
- Delta-based impact detection (2.5g threshold)
- Freefall detection (<0.3g)
- Post-fall stillness analysis (2 sec window)
- HTTP retry (2 attempts)
- RSSI signal warning
- NTP time sync

**State Machine:**
```
IDLE → IMPACT_DETECTED → ANALYZING_FALL → FALL_CONFIRMED → COOLDOWN
                              ↓
                         FALSE ALARM → IDLE
```

**Hardware:**
```
ESP32 GPIO21 → MPU6050 SDA
ESP32 GPIO22 → MPU6050 SCL
ESP32 3.3V   → MPU6050 VCC
ESP32 GND    → MPU6050 GND
```

---

### 2. Cloudflare Worker (worker_v2.1.js)

**Endpoints:**
| Method | Path | Function |
|--------|------|----------|
| POST | `/` | Send FCM + Write to Firestore (FALL_CONFIRMED only) |
| POST | `/writePosture` | Write posture data to Firestore |
| GET | `/health` | Health check (returns version) |

**Environment Variables (set in Cloudflare dashboard):**
- `client_email`: Firebase service account email
- `private_key`: Firebase service account private key
- `project_id`: safestep-leap

**Response Format:**
```json
{
  "fcm": {"statusCode": 200, "body": {...}},
  "firestore": {"statusCode": 200, "path": "devices/ESP32_01/events/evt_xxx"}
}
```

---

### 3. Android App

**Tech Stack:**
- Kotlin + Jetpack Compose
- Material 3 + Dark Theme
- Firebase FCM + Firestore

**Screens:**
| Screen | Purpose |
|--------|---------|
| AlertScreen | Full-screen fall alert with pulsing animation |
| HomeScreen | Dashboard with device status, posture |
| EventHistoryScreen | List of past events |
| SettingsScreen | Toggles + 7-tap hidden developer mode |
| DeveloperScreen | FCM token display, test event injection |
| PairingScreen | Device ID input |

**FCM Handler:** `SafeStepFirebaseService.kt`
- Receives data-only messages
- Launches AlertComposeActivity with full-screen intent
- Works when app is closed/locked

**Design Tokens:**
```kotlin
Primary = #B71C1C (Emergency Red)
Secondary = #FF9800 (Warning Orange)
Tertiary = #2E7D32 (Success Green)
Background = #121212 (Dark)
Surface = #1E1E1E
```

---

### 4. Firestore Schema

```
devices/
└── {device_id}/                    # e.g., "ESP32_01"
    ├── events/
    │   └── {event_id}/             # e.g., "evt_1234567890"
    │       ├── event_type: "FALL_CONFIRMED"
    │       ├── device_id: "ESP32_01"
    │       ├── timestamp: "2026-01-29T..."
    │       ├── impact_g: 3.05
    │       ├── pitch: 12.4
    │       ├── roll: 5.1
    │       ├── acknowledged: false
    │       ├── acknowledged_by: null
    │       └── created_at: "2026-01-29T..."
    │
    └── posture/
        └── latest/
            ├── posture_state: "GOOD" | "WARNING" | "POOR"
            ├── pitch: 5.2
            ├── roll: 3.1
            └── updated_at: "2026-01-29T..."
```

---

## ✅ Current Status (as of 2026-01-29)

| Component | Status | Notes |
|-----------|--------|-------|
| ESP32 Firmware | ✅ Ready | safestep_FINAL.ino with credentials |
| Cloudflare Worker | ✅ Deployed | v2.1 - FCM + Firestore |
| Android App | ✅ Builds | Compose UI complete |
| FCM Push | ✅ Working | Tested successfully |
| Firestore Writes | ✅ Working | Events being recorded |

---

## 🧪 Test Commands

### 1. Health Check
```bash
curl https://safestep-fcm-relay.harishkumar-sp5511.workers.dev/health
```

### 2. Send Test Fall Alert
```bash
curl -X POST "https://safestep-fcm-relay.harishkumar-sp5511.workers.dev/" \
  -H "Content-Type: application/json" \
  -d '{"token":"<FCM_TOKEN>","event_type":"FALL_CONFIRMED","device_id":"ESP32_01","impact_g":"3.05"}'
```

### 3. Build Android APK
```bash
cd Safe-step-wearable
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 Next Steps / TODO

- [ ] Add battery monitoring to ESP32
- [ ] Implement auto-call feature
- [ ] Add posture alerts (prolonged bad posture)
- [ ] UI for viewing historical posture data
- [ ] Add caregiver notification (multiple FCM tokens)
- [ ] Improve fall detection algorithm with ML

---

## 📝 Quick Commands

```bash
# Git
git pull origin main
git add -A && git commit -m "message" && git push

# Android Build
./gradlew assembleDebug

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View FCM logs
adb logcat -s SafeStepFCM
```

---

## 🔗 Resources

- Worker Dashboard: https://dash.cloudflare.com
- Firebase Console: https://console.firebase.google.com/project/safestep-leap
- GitHub Repo: (push your code here!)

---

**Last Updated:** 2026-01-29 10:25 IST
