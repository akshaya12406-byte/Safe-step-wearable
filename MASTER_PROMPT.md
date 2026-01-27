BUILD SAFESTEP APP — MASTER TASK

TASK:
Build a production-ready Android application named "SafeStep" (package: com.safestep.app) plus the minimal Cloud Functions and test harness required to receive confirmed fall events from Firestore and deliver robust emergency notifications via FCM. Follow the PRD below exactly. Do not implement features outside this PRD.

PRD SUMMARY (MUST FOLLOW EXACTLY):
- SafeStep is a mobile emergency endpoint: it receives confirmed FALL_CONFIRMED events from Firestore (path: devices/{device_id}/events/{event_id}), displays a high-priority full-screen alert, and enables immediate phone call action. The app must also provide an event history and simple reliability metrics. All detection occurs on the wearable; the app does not process raw sensor data.
- Firebase role: relay + notify + log. Cloud Function must send FCM when a new event is created. Secure writes via Cloud Function or authenticated tokens.
- No BLE, no GSM, no voice recognition, no live sensor graphs, no ML. Keep scope strict.

TECHNICAL REQUIREMENTS:
- Android (Kotlin). Minimum SDK 26. Target latest stable (API 33/34).
- Use Firebase Firestore (or Realtime DB) for events; use FCM for push.
- Notification CHANNEL: "CHANNEL_EMERGENCY" (IMPORTANCE_HIGH).
- Use NotificationCompat.setFullScreenIntent(pendingIntent, true) to show AlertActivity when appropriate.
- Use PendingIntent flags `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE` where applicable (Android 12+).
- Implement CALL flow: opt-in auto-call with runtime permission `CALL_PHONE`; fallback to ACTION_DIAL if not granted.
- Implement secure Firestore writes: device → Cloud Function → Firestore OR device writes with authenticated token (prefer Cloud Function).
- Provide Cloud Function (Node.js) to trigger FCM on new event. Use FCM HTTP v1 or Admin SDK.
- Implement demo/test harness (Node or Python) to push sample events to Firestore for acceptance testing.

DELIVERABLES (ALL REQUIRED):
1) Android Studio project "SafeStep" (Kotlin) with:
   - Activities: AlertActivity (fullscreen), EventHistoryActivity, SettingsActivity, MainActivity
   - FirebaseMessagingService implementation that creates high-priority full-screen notifications
   - Firestore data layer & models matching schema:
     devices/{device_id}/events/{event_id} event doc with fields: device_id, event_type, timestamp, impact_g, pitch, roll, handled, firmware_version
   - Settings: emergency contact number, auto-call opt-in toggle
   - Permissions manager for CALL_PHONE with rationale dialogs
   - RecyclerView-based EventHistory screen and summary cards (counts, last_seen)
   - Debug/test mode toggle (prevents real calls during demo)
   - Unit tests for FCM handler and data layer; basic instrumented test for AlertActivity UI

2) Cloud Functions:
   - `onEventCreated` function that triggers on Firestore writes to devices/{device_id}/events and sends FCM message to caregiver topic/token with high-priority Android config and full-screen intent extras.
   - Example deploy instructions and `package.json`.

3) Firebase Security Rules sample (restrict writes; allow Cloud Function or authenticated device tokens).

4) Demo harness:
   - Script (Node/Python) to inject sample FALL_CONFIRMED events into Firestore (or call Cloud Function endpoint) for testing.
   - Manual demo checklist (step-by-step).

5) Documentation:
   - README: Firebase project setup, google-services.json placement, Cloud Function deploy, running the app, demo script.
   - Architecture diagram (PNG/SVG) showing device → Firestore → Cloud Function → FCM → App → Call.
   - Acceptance checklist with tests & expected outcomes.

IMPLEMENTATION NOTES (Agent must follow)
- Use latest Firebase and Android SDKs. Consult official docs for `setFullScreenIntent`, `NotificationChannel`, `FCM v1`, and `PendingIntent` flags to ensure compatibility across Android 11–14.
- Use `NotificationCompat.Builder`, `NotificationManagerCompat`, `setFullScreenIntent`.
- Use `PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)`.
- For Cloud Function FCM payload, include `android: { priority: "HIGH", notification: { channel_id: "CHANNEL_EMERGENCY", sound: "default" } }` and include event data in `data` for AlertActivity to read.
- Use Cloud Functions Admin SDK or HTTP v1 endpoint for sending FCM. Ensure server key / service account not committed to repo; document secret management.
- Implement opt-in auto-call with clear explanation; follow runtime permission patterns: show rationale before request; if denied, use ACTION_DIAL fallback.

SECURITY & PRIVACY:
- Do not store raw sensor streams.
- Use Firestore rules to prevent anonymous writes. Use Cloud Function gatekeepers or short-lived device tokens.
- Do not hardcode API keys or service account JSON in source. Provide placeholders and README steps.

ACCEPTANCE CRITERIA:
- The app compiles & runs (Android Studio).
- A Firestore event insertion triggers Cloud Function → FCM → device notification and full-screen AlertActivity.
- CALL action initiates dialer or direct call (permission & opt-in).
- EventHistory shows the created events and status.
- README contains clear setup & test instructions.

QUALITY:
- Provide unit tests & at least one instrumented UI test for AlertActivity.
- Provide demo script and test harness.

OUTPUT FORMAT:
- A git repo skeleton or zip with project files.
- Inline code snippets in responses for key files (FirebaseMessagingService, AlertActivity, Cloud Function).
- README & architecture diagram included.

PROCEED:
1) Acknowledge you understand the PRD & constraints.
2) Provide a step-by-step build plan for the deliverables (order of tasks, approx time estimate per task).
3) Generate the project skeleton (Gradle files, AndroidManifest, stubs for Activities and services).
4) Implement the FCM handler & Cloud Function next.
5) Implement AlertActivity + permissions.
6) Implement EventHistory + settings.
7) Provide test harness & README.

IMPORTANT:
- Do not implement features outside this PRD.
- If any Android API choices require verification (e.g., new flags or restricted behaviors), consult official Android docs and report back before implementation.

If you understand, begin by producing the step-by-step build plan and then the Android project skeleton (files + key code stubs).
