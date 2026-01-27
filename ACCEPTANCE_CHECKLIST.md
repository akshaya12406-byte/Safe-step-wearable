# SafeStep LEAP Competition - Acceptance Checklist

## Build & Compilation

- [ ] App compiles with `./gradlew assembleDebug`
- [ ] No compilation errors or warnings
- [ ] APK generated at `app/build/outputs/apk/debug/app-debug.apk`

## Core Functionality

### FCM & Notifications
- [ ] FCM service registered in AndroidManifest
- [ ] High-priority notification channel created
- [ ] FCM message triggers notification
- [ ] Full-screen intent opens AlertActivity
- [ ] Alert appears over lock screen
- [ ] Alert wakes device from sleep

### AlertActivity
- [ ] Shows device ID and timestamp
- [ ] Shows impact/pitch/roll data
- [ ] CALL button works (opens dialer in non-demo mode)
- [ ] Demo Mode shows toast instead of calling
- [ ] ACKNOWLEDGE button marks event as handled
- [ ] DISMISS button closes activity
- [ ] Back button blocked (requires explicit action)

### Navigation & UI
- [ ] Bottom navigation works (Home / Events / Settings)
- [ ] Home screen shows reliability metrics
- [ ] Device cards display with status indicators
- [ ] Status colors: Green (<2min), Amber (2-10min), Red (>10min)
- [ ] Events list loads from Firestore
- [ ] Swipe-to-refresh works on all screens

### Settings
- [ ] Emergency number saves correctly
- [ ] Demo Mode toggle persists
- [ ] Auto-call toggle with consent dialog
- [ ] Auto-call requests CALL_PHONE permission
- [ ] Developer Mode via 7-tap + PIN

### Developer Mode
- [ ] FCM token displays (masked by default)
- [ ] Long-press reveals full token
- [ ] Copy token works
- [ ] Simulate Event opens AlertActivity

## Firebase Integration

- [ ] Firestore reads events correctly
- [ ] Firestore collection group query works
- [ ] markEventHandled updates Firestore
- [ ] FCM token retrieval works

## Test Harness

- [ ] Python script runs: `python tools/test_fire_event.py --fcm`
- [ ] FCM message arrives on device
- [ ] Full-screen alert displays

## ESP32 Sample

- [ ] `hardware/esp32/send_fcm_example.ino` compiles in Arduino IDE
- [ ] Placeholder comments clear for server key and credentials
- [ ] Security warning documented

## Documentation

- [ ] README has setup instructions
- [ ] README has Firebase configuration steps
- [ ] README has security caveats
- [ ] README has production migration path
- [ ] README has demo script (60-90s)
- [ ] README has OEM battery optimization notes

## Design & Accessibility

- [ ] Dark theme implemented
- [ ] Large buttons for elderly users
- [ ] Text sizes appropriate (16-24sp for body)
- [ ] Alert buttons 72dp+ height
- [ ] Color contrast meets accessibility guidelines

## Safety Features

- [ ] Demo Mode prevents real calls (DEFAULT OFF)
- [ ] Auto-call requires explicit opt-in (DEFAULT OFF)
- [ ] Auto-call shows consent dialog
- [ ] CALL_PHONE permission required for auto-call

---

## Verification Log

| Date | Tester | Status | Notes |
|------|--------|--------|-------|
| | | | |

---

## Sign-Off

- [ ] All critical items verified
- [ ] Demo script rehearsed
- [ ] APK ready for submission

**Submitted by:** _________________  
**Date:** _________________
