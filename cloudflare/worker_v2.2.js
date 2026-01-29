// ============================================================
// SafeStep FCM Relay Worker - v2.2
// FCM + Firestore Events + Device Status + Heartbeat
// ============================================================
// 
// Environment Variables Required:
// - client_email: Firebase service account email
// - private_key: Firebase service account private key
// - project_id: Firebase project ID
//
// Endpoints:
// - POST /           : FCM + Firestore event + device status update
// - POST /heartbeat  : Device heartbeat (updates last_seen)
// - POST /writePosture : Write posture data
// - GET /health      : Health check
// ============================================================

// Token cache
let tokenCache = {
    fcm: { token: null, expiry: 0 },
    firestore: { token: null, expiry: 0 }
};

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
                return handleFCMSendWithFirestore(request, env);
            }
        }

        if (request.method === "GET" && url.pathname === "/health") {
            return corsResponse(JSON.stringify({
                status: "ok",
                version: "2.2",
                features: ["fcm", "firestore_events", "device_status", "heartbeat"]
            }), 200);
        }

        return corsResponse(JSON.stringify({
            error: "Method not allowed",
            endpoints: ["POST /", "POST /heartbeat", "POST /writePosture", "GET /health"]
        }), 405);
    },
};

// ============================================================
// HEARTBEAT HANDLER - Updates device last_seen
// ============================================================
async function handleHeartbeat(request, env) {
    try {
        const body = await request.json();

        if (!body.device_id) {
            return corsResponse(JSON.stringify({ error: "Missing device_id" }), 400);
        }

        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({ error: "Missing env vars" }), 500);
        }

        const token = await getCachedAccessToken(env, "firestore");
        if (!token) {
            return corsResponse(JSON.stringify({ error: "Auth failed" }), 500);
        }

        // Write to devices/{device_id}/meta/info
        const docPath = `devices/${body.device_id}/meta/info`;
        const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${docPath}`;

        const firestoreDoc = {
            fields: {
                device_id: { stringValue: body.device_id },
                last_seen: { timestampValue: new Date().toISOString() },
                battery_pct: { integerValue: parseInt(body.battery_pct) || -1 },
                fw_version: { stringValue: body.fw_version || "" },
                name: { stringValue: body.name || body.device_id },
                rssi: { integerValue: parseInt(body.rssi) || 0 }
            }
        };

        const res = await fetch(firestoreUrl, {
            method: "PATCH",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(firestoreDoc)
        });

        if (res.ok) {
            return corsResponse(JSON.stringify({
                success: true,
                path: docPath,
                timestamp: new Date().toISOString()
            }), 200);
        } else {
            const err = await res.text();
            return corsResponse(JSON.stringify({ error: "Firestore failed", detail: err }), res.status);
        }

    } catch (e) {
        return corsResponse(JSON.stringify({ error: e.message }), 500);
    }
}

// ============================================================
// FCM + FIRESTORE + DEVICE STATUS HANDLER
// ============================================================
async function handleFCMSendWithFirestore(request, env) {
    let fcmResult = { statusCode: 0, body: "" };
    let firestoreResult = { statusCode: 0, body: "", path: "" };
    let deviceResult = { statusCode: 0, body: "" };

    try {
        const body = await request.json();

        if (!body.token || !body.event_type) {
            return corsResponse(JSON.stringify({ error: "Missing token or event_type" }), 400);
        }

        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({ error: "Missing env vars" }), 500);
        }

        const eventId = body.event_id || `evt_${Date.now()}`;
        const deviceId = body.device_id || "ESP32";
        const timestamp = body.timestamp || new Date().toISOString();

        // ---- STEP 1: Send FCM ----
        const fcmToken = await getCachedAccessToken(env, "fcm");
        if (!fcmToken) {
            return corsResponse(JSON.stringify({ error: "FCM auth failed" }), 500);
        }

        const fcmPayload = {
            message: {
                token: body.token,
                android: { priority: "HIGH" },
                data: {
                    event_type: body.event_type,
                    device_id: deviceId,
                    timestamp: timestamp,
                    impact_g: body.impact_g || "",
                    pitch: body.pitch || "",
                    roll: body.roll || "",
                    event_id: eventId,
                    firmware_version: body.firmware_version || ""
                }
            }
        };

        const fcmRes = await fetch(
            `https://fcm.googleapis.com/v1/projects/${env.project_id}/messages:send`,
            {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${fcmToken}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(fcmPayload)
            }
        );

        fcmResult.statusCode = fcmRes.status;
        fcmResult.body = await fcmRes.text();

        // ---- STEP 2: Write Event to Firestore (FALL_CONFIRMED only) ----
        if (body.event_type === "FALL_CONFIRMED") {
            try {
                const firestoreToken = await getCachedAccessToken(env, "firestore");
                if (firestoreToken) {
                    const docPath = `devices/${deviceId}/events/${eventId}`;
                    const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${docPath}`;

                    const firestoreDoc = {
                        fields: {
                            event_id: { stringValue: eventId },
                            device_id: { stringValue: deviceId },
                            event_type: { stringValue: body.event_type },
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

                    const firestoreRes = await fetch(firestoreUrl, {
                        method: "PATCH",
                        headers: {
                            Authorization: `Bearer ${firestoreToken}`,
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify(firestoreDoc)
                    });

                    firestoreResult.statusCode = firestoreRes.status;
                    firestoreResult.body = await firestoreRes.text();
                    firestoreResult.path = docPath;
                }
            } catch (fsErr) {
                firestoreResult.statusCode = 500;
                firestoreResult.body = fsErr.message;
            }
        } else {
            firestoreResult.body = "Skipped (not FALL_CONFIRMED)";
        }

        // ---- STEP 3: Update Device Status (last_seen) ----
        try {
            const token = await getCachedAccessToken(env, "firestore");
            if (token) {
                const metaPath = `devices/${deviceId}/meta/info`;
                const metaUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${metaPath}`;

                const metaDoc = {
                    fields: {
                        device_id: { stringValue: deviceId },
                        name: { stringValue: deviceId },
                        last_seen: { timestampValue: new Date().toISOString() },
                        fw_version: { stringValue: body.firmware_version || "" },
                        battery_pct: { integerValue: -1 }
                    }
                };

                const metaRes = await fetch(metaUrl, {
                    method: "PATCH",
                    headers: {
                        Authorization: `Bearer ${token}`,
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(metaDoc)
                });

                deviceResult.statusCode = metaRes.status;
                deviceResult.body = metaRes.ok ? "Device status updated" : await metaRes.text();
            }
        } catch (e) {
            deviceResult.body = e.message;
        }

        // ---- Return combined result ----
        return corsResponse(JSON.stringify({
            fcm: { statusCode: fcmResult.statusCode, body: tryParseJSON(fcmResult.body) },
            firestore: { statusCode: firestoreResult.statusCode, path: firestoreResult.path },
            device: { statusCode: deviceResult.statusCode, body: deviceResult.body }
        }), fcmResult.statusCode >= 200 && fcmResult.statusCode < 300 ? 200 : fcmResult.statusCode);

    } catch (e) {
        return corsResponse(JSON.stringify({ error: e.message }), 500);
    }
}

// ============================================================
// POSTURE WRITE HANDLER
// ============================================================
async function handlePostureWrite(request, env) {
    try {
        const body = await request.json();

        if (!body.device_id || !body.posture_state) {
            return corsResponse(JSON.stringify({ error: "Missing device_id or posture_state" }), 400);
        }

        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({ error: "Missing env vars" }), 500);
        }

        const token = await getCachedAccessToken(env, "firestore");
        if (!token) {
            return corsResponse(JSON.stringify({ error: "Auth failed" }), 500);
        }

        const docPath = `devices/${body.device_id}/posture/latest`;
        const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${docPath}`;

        const firestoreDoc = {
            fields: {
                device_id: { stringValue: body.device_id },
                posture_state: { stringValue: body.posture_state },
                pitch: { doubleValue: parseFloat(body.pitch) || 0 },
                roll: { doubleValue: parseFloat(body.roll) || 0 },
                timestamp: { timestampValue: body.timestamp || new Date().toISOString() },
                updated_at: { timestampValue: new Date().toISOString() }
            }
        };

        const res = await fetch(firestoreUrl, {
            method: "PATCH",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(firestoreDoc)
        });

        if (res.ok) {
            return corsResponse(JSON.stringify({ success: true, path: docPath }), 200);
        } else {
            const err = await res.text();
            return corsResponse(JSON.stringify({ error: "Firestore failed", detail: err }), res.status);
        }

    } catch (e) {
        return corsResponse(JSON.stringify({ error: e.message }), 500);
    }
}

// ============================================================
// TOKEN CACHING
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
        tokenCache[cacheKey] = { token: token, expiry: now + 3600 };
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
        btoa(JSON.stringify(obj))
            .replace(/\+/g, "-")
            .replace(/\//g, "_")
            .replace(/=+$/, "");

    const unsignedJWT = `${base64url(header)}.${base64url(claim)}`;

    let pem = env.private_key;
    pem = pem.replace(/\\n/g, "").replace(/\n/g, "").replace(/\r/g, "");
    pem = pem.replace("-----BEGIN PRIVATE KEY-----", "");
    pem = pem.replace("-----END PRIVATE KEY-----", "");
    pem = pem.replace(/\s/g, "");

    let binaryKey;
    try {
        binaryKey = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    } catch (e) {
        return null;
    }

    let cryptoKey;
    try {
        cryptoKey = await crypto.subtle.importKey(
            "pkcs8",
            binaryKey.buffer,
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
            false,
            ["sign"]
        );
    } catch (e) {
        return null;
    }

    const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        cryptoKey,
        new TextEncoder().encode(unsignedJWT)
    );

    const signedJWT =
        unsignedJWT + "." +
        btoa(String.fromCharCode(...new Uint8Array(signature)))
            .replace(/\+/g, "-")
            .replace(/\//g, "_")
            .replace(/=+$/, "");

    const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJWT}`
    });

    const tokenData = await tokenRes.json();
    return tokenData.access_token || null;
}

function tryParseJSON(str) {
    try { return JSON.parse(str); } catch { return str; }
}

function corsResponse(body, status) {
    return new Response(body, {
        status: status,
        headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type"
        }
    });
}
