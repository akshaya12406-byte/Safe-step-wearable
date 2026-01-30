// ============================================================
// SafeStep FCM Relay Worker - v3.0 (CAREGIVER MODE)
// Multi-recipient FCM + Firestore Events + Device Status
// ============================================================
// 
// CAREGIVER MODE: Looks up caregiver tokens from Firestore,
// sends FCM to ALL registered caregivers for a device.
//
// Environment Variables Required:
// - client_email: Firebase service account email
// - private_key: Firebase service account private key
// - project_id: Firebase project ID
//
// Endpoints:
// - POST /           : Multi-recipient FCM + Firestore event
// - POST /heartbeat  : Device heartbeat (updates last_seen)
// - POST /writePosture : Write posture data
// - GET /health      : Health check
// ============================================================

let tokenCache = { fcm: { token: null, expiry: 0 }, firestore: { token: null, expiry: 0 } };

export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        if (request.method === "OPTIONS") {
            return corsResponse(null, 204);
        }

        if (request.method === "POST") {
            const path = url.pathname.toLowerCase();
            if (path === "/writeposture") {
                return handlePostureWrite(request, env);
            } else if (path === "/heartbeat") {
                return handleHeartbeat(request, env);
            } else {
                return handleFallAlert(request, env);
            }
        }

        if (request.method === "GET" && url.pathname === "/health") {
            return corsResponse(JSON.stringify({
                status: "ok",
                version: "3.0",
                mode: "caregiver",
                features: ["multi_recipient_fcm", "firestore_events", "device_status", "posture"]
            }), 200);
        }

        return corsResponse(JSON.stringify({ error: "Method not allowed" }), 405);
    },
};

// ============================================================
// FALL ALERT HANDLER (Multi-recipient FCM)
// ============================================================
async function handleFallAlert(request, env) {
    const result = {
        fcmResults: [],
        firestoreEvent: { statusCode: 0, path: "" },
        deviceStatus: { statusCode: 0 },
        warnings: []
    };

    try {
        const body = await request.json();
        const deviceId = body.device_id;

        if (!deviceId) {
            return corsResponse(JSON.stringify({ error: "Missing device_id" }), 400);
        }

        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({ error: "Missing env vars" }), 500);
        }

        const eventId = body.event_id || `evt_${Date.now()}`;
        const timestamp = body.timestamp || new Date().toISOString();
        const eventType = body.event_type || "FALL_CONFIRMED";

        // ---- STEP 1: Lookup caregiver tokens from Firestore ----
        let caregiverTokens = [];
        try {
            caregiverTokens = await getCaregiverTokens(env, deviceId);
            console.log(`Found ${caregiverTokens.length} caregivers for device ${deviceId}`);
        } catch (lookupError) {
            result.warnings.push(`Caregiver lookup failed: ${lookupError.message}`);
        }

        // Fallback to body.token if no caregivers found
        if (caregiverTokens.length === 0 && body.token) {
            caregiverTokens = [{ token: body.token, name: "legacy_token" }];
            result.warnings.push("No caregivers found, using fallback token from body");
        }

        if (caregiverTokens.length === 0) {
            result.warnings.push("No recipients found - alert not sent");
        }

        // ---- STEP 2: Send FCM to each caregiver ----
        const fcmToken = await getCachedAccessToken(env, "fcm");
        if (!fcmToken) {
            return corsResponse(JSON.stringify({ error: "FCM auth failed" }), 500);
        }

        const fcmPayload = {
            android: { priority: "HIGH" },
            data: {
                event_type: eventType,
                device_id: deviceId,
                timestamp: timestamp,
                impact_g: body.impact_g || "",
                pitch: body.pitch || "",
                roll: body.roll || "",
                event_id: eventId,
                firmware_version: body.firmware_version || ""
            }
        };

        for (const caregiver of caregiverTokens) {
            try {
                const fcmRes = await fetch(
                    `https://fcm.googleapis.com/v1/projects/${env.project_id}/messages:send`,
                    {
                        method: "POST",
                        headers: {
                            Authorization: `Bearer ${fcmToken}`,
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            message: { token: caregiver.token, ...fcmPayload }
                        })
                    }
                );

                const fcmResultBody = await fcmRes.text();
                result.fcmResults.push({
                    caregiver: caregiver.name || "unknown",
                    statusCode: fcmRes.status,
                    success: fcmRes.status === 200,
                    body: tryParseJSON(fcmResultBody)
                });
            } catch (fcmErr) {
                result.fcmResults.push({
                    caregiver: caregiver.name || "unknown",
                    statusCode: 500,
                    success: false,
                    error: fcmErr.message
                });
            }
        }

        // ---- STEP 3: Write event to Firestore (FALL_CONFIRMED only) ----
        if (eventType === "FALL_CONFIRMED") {
            try {
                const firestoreToken = await getCachedAccessToken(env, "firestore");
                const docPath = `devices/${deviceId}/events/${eventId}`;
                const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${docPath}`;

                const firestoreDoc = {
                    fields: {
                        event_id: { stringValue: eventId },
                        device_id: { stringValue: deviceId },
                        event_type: { stringValue: eventType },
                        timestamp: { timestampValue: timestamp },
                        impact_g: { doubleValue: parseFloat(body.impact_g) || 0 },
                        pitch: { doubleValue: parseFloat(body.pitch) || 0 },
                        roll: { doubleValue: parseFloat(body.roll) || 0 },
                        firmware_version: { stringValue: body.firmware_version || "" },
                        acknowledged: { booleanValue: false },
                        acknowledged_by: { nullValue: null },
                        acknowledged_at: { nullValue: null },
                        created_at: { timestampValue: new Date().toISOString() }
                    }
                };

                const res = await fetch(firestoreUrl, {
                    method: "PATCH",
                    headers: { Authorization: `Bearer ${firestoreToken}`, "Content-Type": "application/json" },
                    body: JSON.stringify(firestoreDoc)
                });

                result.firestoreEvent.statusCode = res.status;
                result.firestoreEvent.path = docPath;
                if (!res.ok) {
                    result.firestoreEvent.error = await res.text();
                }
            } catch (fsErr) {
                result.firestoreEvent.error = fsErr.message;
            }
        }

        // ---- STEP 4: Update device status (last_seen) ----
        try {
            const token = await getCachedAccessToken(env, "firestore");
            const metaPath = `devices/${deviceId}/meta/info`;
            const metaUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${metaPath}`;

            const metaDoc = {
                fields: {
                    device_id: { stringValue: deviceId },
                    name: { stringValue: deviceId },
                    last_seen: { timestampValue: new Date().toISOString() },
                    fw_version: { stringValue: body.firmware_version || "" }
                }
            };

            const res = await fetch(metaUrl, {
                method: "PATCH",
                headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
                body: JSON.stringify(metaDoc)
            });
            result.deviceStatus.statusCode = res.status;
        } catch (e) {
            result.deviceStatus.error = e.message;
        }

        const overallSuccess = result.fcmResults.some(r => r.success);
        return corsResponse(JSON.stringify(result), overallSuccess ? 200 : 500);

    } catch (e) {
        return corsResponse(JSON.stringify({ error: e.message }), 500);
    }
}

// ============================================================
// LOOKUP CAREGIVER TOKENS FROM FIRESTORE
// ============================================================
async function getCaregiverTokens(env, deviceId) {
    const token = await getCachedAccessToken(env, "firestore");
    if (!token) throw new Error("Auth failed");

    const url = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/devices/${deviceId}/caregivers`;

    const res = await fetch(url, {
        headers: { Authorization: `Bearer ${token}` }
    });

    if (!res.ok) {
        if (res.status === 404) return []; // No caregivers collection
        throw new Error(`Firestore lookup failed: ${res.status}`);
    }

    const data = await res.json();
    const tokens = [];

    if (data.documents && Array.isArray(data.documents)) {
        for (const doc of data.documents) {
            const fields = doc.fields || {};
            if (fields.fcm_token && fields.fcm_token.stringValue) {
                tokens.push({
                    token: fields.fcm_token.stringValue,
                    name: fields.name?.stringValue || "caregiver",
                    phone: fields.phone?.stringValue || ""
                });
            }
        }
    }

    return tokens;
}

// ============================================================
// HEARTBEAT HANDLER
// ============================================================
async function handleHeartbeat(request, env) {
    try {
        const body = await request.json();
        if (!body.device_id) {
            return corsResponse(JSON.stringify({ error: "Missing device_id" }), 400);
        }

        const token = await getCachedAccessToken(env, "firestore");
        const docPath = `devices/${body.device_id}/meta/info`;
        const url = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${docPath}`;

        const doc = {
            fields: {
                device_id: { stringValue: body.device_id },
                last_seen: { timestampValue: new Date().toISOString() },
                battery_pct: { integerValue: parseInt(body.battery_pct) || -1 },
                fw_version: { stringValue: body.fw_version || "" },
                name: { stringValue: body.name || body.device_id },
                rssi: { integerValue: parseInt(body.rssi) || 0 }
            }
        };

        const res = await fetch(url, {
            method: "PATCH",
            headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify(doc)
        });

        return corsResponse(JSON.stringify({ success: res.ok, path: docPath }), res.ok ? 200 : 500);
    } catch (e) {
        return corsResponse(JSON.stringify({ error: e.message }), 500);
    }
}

// ============================================================
// POSTURE WRITE HANDLER
// Now also updates device meta/info for online status tracking!
// ============================================================
async function handlePostureWrite(request, env) {
    try {
        const body = await request.json();
        if (!body.device_id || !body.posture_state) {
            return corsResponse(JSON.stringify({ error: "Missing device_id or posture_state" }), 400);
        }

        const token = await getCachedAccessToken(env, "firestore");
        const now = new Date().toISOString();

        // ---- STEP 1: Write posture data ----
        const posturePath = `devices/${body.device_id}/posture/latest`;
        const postureUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${posturePath}`;

        const postureDoc = {
            fields: {
                device_id: { stringValue: body.device_id },
                posture_state: { stringValue: body.posture_state },
                pitch: { doubleValue: parseFloat(body.pitch) || 0 },
                roll: { doubleValue: parseFloat(body.roll) || 0 },
                timestamp: { timestampValue: body.timestamp || now },
                updated_at: { timestampValue: now }
            }
        };

        const postureRes = await fetch(postureUrl, {
            method: "PATCH",
            headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify(postureDoc)
        });

        // ---- STEP 2: Also update device meta/info for online status ----
        // This ensures device shows "Online" whenever posture is being sent
        const metaPath = `devices/${body.device_id}/meta/info`;
        const metaUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${metaPath}`;

        const metaDoc = {
            fields: {
                device_id: { stringValue: body.device_id },
                name: { stringValue: body.device_id },
                last_seen: { timestampValue: now },
                battery_pct: { integerValue: parseInt(body.battery_pct) || -1 },
                fw_version: { stringValue: body.fw_version || "" }
            }
        };

        const metaRes = await fetch(metaUrl, {
            method: "PATCH",
            headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify(metaDoc)
        });

        return corsResponse(JSON.stringify({
            success: postureRes.ok,
            posturePath: posturePath,
            deviceStatusUpdated: metaRes.ok
        }), postureRes.ok ? 200 : 500);
    } catch (e) {
        return corsResponse(JSON.stringify({ error: e.message }), 500);
    }
}

// ============================================================
// TOKEN CACHING & AUTH
// ============================================================
async function getCachedAccessToken(env, type) {
    const now = Math.floor(Date.now() / 1000);
    const cacheKey = type === "fcm" ? "fcm" : "firestore";
    const scope = type === "fcm"
        ? "https://www.googleapis.com/auth/firebase.messaging"
        : "https://www.googleapis.com/auth/datastore";

    if (tokenCache[cacheKey].token && tokenCache[cacheKey].expiry > now + 300) {
        return tokenCache[cacheKey].token;
    }

    const token = await getFirebaseAccessToken(env, scope);
    if (token) {
        tokenCache[cacheKey] = { token, expiry: now + 3600 };
    }
    return token;
}

async function getFirebaseAccessToken(env, scope) {
    const now = Math.floor(Date.now() / 1000);
    const header = { alg: "RS256", typ: "JWT" };
    const claim = {
        iss: env.client_email,
        scope: scope,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600
    };

    const base64url = (obj) =>
        btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

    const unsignedJWT = `${base64url(header)}.${base64url(claim)}`;

    let pem = env.private_key.replace(/\\n/g, "").replace(/\n/g, "").replace(/\r/g, "")
        .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(/\s/g, "");

    let binaryKey;
    try { binaryKey = Uint8Array.from(atob(pem), c => c.charCodeAt(0)); }
    catch { return null; }

    let cryptoKey;
    try {
        cryptoKey = await crypto.subtle.importKey("pkcs8", binaryKey.buffer,
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
    } catch { return null; }

    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, new TextEncoder().encode(unsignedJWT));
    const signedJWT = unsignedJWT + "." + btoa(String.fromCharCode(...new Uint8Array(signature)))
        .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

    const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJWT}`
    });

    const tokenData = await tokenRes.json();
    return tokenData.access_token || null;
}

function tryParseJSON(str) { try { return JSON.parse(str); } catch { return str; } }

function corsResponse(body, status) {
    return new Response(body, {
        status,
        headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type"
        }
    });
}
