# SafeStep

SafeStep is a reliable emergency response application for wearable fall detectors. It receives confirmed fall events via Firebase Cloud Messaging (FCM) and displays high-priority full-screen alerts to caregivers.

> ⚠️ **Firebase Spark Plan Compatible**: This app uses only Firebase Spark (free) features. No Cloud Functions required.

## Architecture

```
ESP32 Wearable ──► HTTPS POST ──► FCM API ──► SafeStep Android App ──► Emergency Call
      │                                              │
      └──────────────► Firestore ◄─────────────────►┘
                     (Event History)
```

**Key Points:**
- ESP32 sends FCM messages **directly** via HTTPS (no Cloud Functions)
- App receives high-priority push notifications and wakes device
- Firestore stores event history for dashboard (read-only from app)
- No Blaze plan required

## Features

- ✅ Full-screen emergency alerts (wakes locked screens)
- ✅ One-tap emergency calling
- ✅ Auto-call opt-in with permission handling
- ✅ Demo Mode (prevents real calls during testing)
- ✅ Event history dashboard
- ✅ Works with Firebase Spark (free) plan

## Setup Instructions

### 1. Firebase Setup (Spark Plan)
1. Create a Firebase Project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android App with package name `com.safestep.app`
3. Download `google-services.json` and place it in `app/google-services.json`
4. Enable **Firestore Database** (start in test mode for development)
5. Get your **FCM Server Key** from Project Settings → Cloud Messaging

### 2. ESP32 Configuration
The ESP32 must send FCM messages directly via HTTPS POST:

```cpp
// ESP32 sends to: https://fcm.googleapis.com/fcm/send
// Headers:
//   Authorization: key=YOUR_FCM_SERVER_KEY
//   Content-Type: application/json
// Body:
{
  "to": "/topics/caregiver",  // or device token
  "priority": "high",
  "data": {
    "event_type": "FALL_CONFIRMED",
    "device_id": "ESP32_01",
    "timestamp": "2026-01-27T15:50:00Z",
    "impact_g": "3.05"
  }
}
```

### 3. Build & Run
1. Open the project in Android Studio
2. Sync Gradle
3. Run on device/emulator (API 26+)
4. **Grant Permissions**: Allow notifications when prompted

### 4. Testing
1. Enable **Demo Mode** in Settings (ON by default)
2. Use the "Simulate Alert" button in the app
3. Or send a test FCM message from ESP32 / Postman
4. Disable Demo Mode for production use

## OEM-Specific Notes

Some manufacturers restrict full-screen intents. You may need to:
- **Xiaomi/MIUI**: Settings → Apps → SafeStep → Other permissions → Display pop-up windows
- **Oppo/Vivo**: Settings → App management → SafeStep → Display over other apps
- **Samsung**: Usually works out of the box

## Security Considerations

> ⚠️ **Prototype Security**: This is a hackathon prototype. For production:
> - Use Firebase Auth for user identity
> - Validate ESP32 writes with custom tokens
> - Never expose FCM server key in client code

### Current Security Model:
- ESP32 uses FCM server key (stored securely on device)
- Firestore allows public reads (for caregiver dashboard)
- Firestore allows public creates (for ESP32 event logging)
- Events are immutable (no updates/deletes)

## Project Structure

```
app/
├── src/main/java/com/safestep/app/
│   ├── SafeStepApplication.kt    # Notification channel setup
│   ├── service/
│   │   └── MyFirebaseMessagingService.kt  # FCM handler
│   ├── ui/
│   │   ├── MainActivity.kt       # Dashboard
│   │   ├── AlertActivity.kt      # Full-screen alert
│   │   ├── EventHistoryActivity.kt
│   │   └── SettingsActivity.kt   # Emergency contact, auto-call, demo mode
│   ├── data/
│   │   └── EventRepository.kt    # Firestore access
│   └── model/
│       └── Event.kt
└── src/main/res/layout/          # UI layouts
```

## Acceptance Checklist

- [ ] App compiles and runs in Android Studio
- [ ] FCM message triggers full-screen alert on locked device
- [ ] "CALL" button opens dialer (or calls if auto-call enabled)
- [ ] Demo Mode shows toast instead of calling
- [ ] Event History displays events from Firestore
- [ ] Settings persist across app restarts
