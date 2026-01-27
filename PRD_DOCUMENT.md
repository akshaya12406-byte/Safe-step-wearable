# PRD — SafeStep (Product Requirements Document)

## 1. Project overview (one line)

**SafeStep** is a minimal, highly reliable Android application that receives confirmed fall events from a wearable (ESP32+MPU6050) via Firebase, wakes the phone if needed, shows a full-screen emergency alert, and enables immediate phone-based response (call), plus a compact read-only event & reliability dashboard for evaluation.

---

## 2. Vision & goals

* **Vision:** Deliver a dependable emergency response endpoint for a low-cost wearable fall detector.
* **Primary goals:**

  * Guarantee alert delivery to caregiver phones even when app is closed/locked.
  * Provide immediate one-tap call capability (or auto-call with explicit opt-in).
  * Provide verifiable event history and simple reliability metrics for LEAP evaluation.
* **Success metrics:**

  * FCM notification delivered within <5s under normal network conditions.
  * Full-screen alert appears reliably on locked screens (subject to OEM variations).
  * Call initiated (or dialer opened) with one user action.
  * Event history and metrics available in app and backed by Firestore.

---

## 3. Non-functional requirements

* Platform: **Native Android (Kotlin)**. Min SDK: **26**. Target: latest stable (API 33/34).
* Firebase: **Firestore** (or Realtime DB) for events; **FCM** for push.
* Security: secure Firestore rules, device write gatekeeping (recommended Cloud Function validation).
* Privacy: no raw sensor streams stored; only minimal event fields.
* Reliability: no background polling; rely on FCM to wake device.
* Usability: clear large-font emergency UI; accessible (TalkBack).
* Consent/legal: explicit opt-in for `CALL_PHONE` and auto-call; fallback to `ACTION_DIAL`.

---

## 4. Scope (in / out)

**In scope**

* Native Android app package: `com.safestep.app`
* FCM high-priority notifications with full-screen intent.
* AlertActivity (full screen), EventHistoryActivity, SettingsActivity.
* Firestore schema + Cloud Function that sends FCM on new `FALL_CONFIRMED`.
* Runtime permission management (CALL_PHONE).
* Demo/test harness and acceptance tests (unit + instrumentation).
* Read-only dashboard: event list + simple metrics cards.

**Out of scope**

* Any fall detection or sensor processing in app.
* BLE or direct device pairing.
* Live sensor graphs or streaming telemetry.
* Speech recognition or AI in-app.
* GSM/SIM based calling from device.

---

## 5. Users & stories

* **Caregiver**: receive push alerts, call quickly, view history.
* **Device owner**: view posture history & events.
* **Judge / Evaluator**: inspect event logs, metrics & reliability evidence.

Key stories:

* As caregiver I receive a noticeable full-screen alert if a fall is confirmed.
* As caregiver I can call the emergency contact with one tap.
* As evaluator I can see event history and reliability metrics.

---

## 6. Data model (Firestore recommended schema)

**Collection:** `devices/{device_id}/events/{event_id}`
Event doc:

```json
{
  "device_id": "ESP32_01",
  "event_type": "FALL_CONFIRMED",   // "POSTURE_BAD"
  "timestamp": "2026-01-27T15:50:00Z",
  "impact_g": 3.05,
  "pitch": 12.4,
  "roll": 5.1,
  "handled": false,
  "acknowledged_by": null,
  "firmware_version": "v1.2.0"
}
```

**Doc:** `devices/{device_id}/meta`

```json
{
  "last_seen": "2026-01-27T15:50:24Z",
  "battery_pct": 87,
  "fw_version": "v1.2.0"
}
```

Security recommendation:

* Do **not** allow unauthenticated direct writes from arbitrary clients. Prefer:

  * ESP32 → HTTPS Cloud Function (with a device token) → Cloud Function writes to Firestore; OR
  * ESP32 writes only with an authenticated token provisioned securely.

---

## 7. Notification & UX design (critical)

### Notification behavior

* **FCM payload**: send high-priority (Android: `priority: "HIGH"`, `android: { priority: "HIGH" }`).
* **Notification Channel**: `CHANNEL_EMERGENCY`, importance `IMPORTANCE_HIGH`.
* Use `NotificationCompat.Builder.setFullScreenIntent(pendingIntent, true)` to trigger full-screen alert activity.
* Use `PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE` (Android 12+ compatibility).

### AlertActivity UI

* Full-screen, forceful, high-contrast layout.
* Big title: **“FALL DETECTED”**.
* Timestamp, device name.
* Prominent **CALL** button; smaller **ACKNOWLEDGE** / **CANCEL**.
* Countdown or subtle vibration pattern optional.
* Accessibility: TalkBack labels, large buttons.

### Call flow

* If **auto-call opt-in** AND `CALL_PHONE` granted → `ACTION_CALL` (direct call).
* Else fallback → `ACTION_DIAL` (pre-filled number).
* Permission rationale dialog must explain consequence.

---

## 8. Permissions & runtime flows

* `INTERNET` (normal)
* `WAKE_LOCK` (optional; careful)
* `RECEIVE_BOOT_COMPLETED` (optional)
* `CALL_PHONE` (dangerous) — request at runtime with clear explanation.
* If user denies, fall back to dialer.

User consent:

* Auto-call must be explicitly enabled in Settings with explanation and confirmation.

---

## 9. Cloud Functions & FCM

* A Cloud Function triggers on new Firestore event creation under `devices/{device_id}/events/{event_id}` and sends an FCM message to the caregiver topic or specific registration tokens.
* Payload should include:

  * `data` (device_id, timestamp, event_type, impact_g, pitch, roll)
  * Notification fields for human display
  * Android config: `priority: "HIGH"`, `notification.channel_id: "CHANNEL_EMERGENCY"`

Security:

* Validate incoming writes if using direct device writes; otherwise, require ESP32 to post to authenticated Cloud Function endpoint.

---

## 10. Dashboard (in-app, minimal & read-only)

**Event list** (RecyclerView): event type, timestamp, device id, handled status.
**Summary cards**: total events, false alerts (from testing), avg latency, last_seen.
No live plots. No control widgets.

---

## 11. Testing & acceptance criteria

**Functional checks**

* FCM notification arrives and displays full-screen when app closed/locked.
* AlertActivity opens and CALL/DIAL behaves correctly.
* Events appear in EventHistory and Firestore.
* Acknowledge sets `handled=true` in Firestore.
* Demo script: normal movement → no alert; staged fall → alert + call; near-fall → no alert.

**Metrics to collect**

* True Positive Rate (TPR)
* False Positive Rate (FPR) per hour
* Average detection latency

**Acceptance**

* App compiles and runs in Android Studio (Min SDK 26).
* Cloud Function deploys and triggers push for a sample event.
* README contains Firebase setup instructions and demo steps.

---

## 12. Deliverables (what agent must produce)

1. Android Studio project: package `com.safestep.app`
2. Activities: `AlertActivity`, `EventHistoryActivity`, `SettingsActivity`, `MainActivity`
3. `FirebaseMessagingService` implementation
4. Firestore repository & models
5. Cloud Function (Node.js) for FCM trigger and sample deploy steps
6. Firebase security rules example
7. Demo harness + test scripts (Node/Python) to inject sample events
8. README, architecture diagram, acceptance checklist, unit/instrumented tests

---

## 13. Risks & mitigations

* **OEM & DND differences**: mitigate by using high-priority FCM & document OEM caveats; test on multiple devices.
* **Call permission denial**: fallback to dialer; warn user.
* **Unauthenticated writes**: force device→CloudFunction→Firestore flow.
* **FCM throttling**: keep payloads small and high-priority only for true events.

---

## 14. How to explain to a judge (one sentence)

> “SafeStep receives confirmed fall events from the wearable and guarantees delivery to caregiver phones using Firebase; the app’s sole role is reliable, immediate human action and evidence storage — all detection runs on the wearable.”
