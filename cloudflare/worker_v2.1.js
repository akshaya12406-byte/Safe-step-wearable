// ============================================================
// SafeStep FCM Relay Worker - v2.1
// With Firestore Event Writes + Token Caching
// ============================================================
// Deploy to Cloudflare Workers
//
// Environment Variables Required:
// - client_email: Firebase service account email
// - private_key: Firebase service account private key (PEM format)
// - project_id: Firebase project ID
//
// Endpoints:
// - POST /           : Send FCM + write FALL_CONFIRMED to Firestore
// - POST /writePosture : Write posture data to Firestore
// - GET /health      : Health check
// ============================================================

// ============================================================
// TOKEN CACHE (Global - persists across requests in same isolate)
// ============================================================
let tokenCache = {
    fcm: { token: null, expiry: 0 },
    firestore: { token: null, expiry: 0 }
};

export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        // CORS preflight
        if (request.method === "OPTIONS") {
            return corsResponse(null, 204);
        }

        // Route handling
        if (request.method === "POST") {
            if (url.pathname === "/writePosture" || url.pathname === "/writeposture") {
                return handlePostureWrite(request, env);
            } else {
                // Root path: FCM send + Firestore write for FALL_CONFIRMED
                return handleFCMSendWithFirestore(request, env);
            }
        }

        if (request.method === "GET" && url.pathname === "/health") {
            return corsResponse(JSON.stringify({
                status: "ok",
                version: "2.1",
                firestore_events_enabled: true,
                token_cache_active: tokenCache.fcm.token !== null
            }), 200);
        }

        return corsResponse(JSON.stringify({
            error: "Method not allowed",
            endpoints: ["POST / (FCM+Firestore)", "POST /writePosture", "GET /health"]
        }), 405);
    },
};

// ============================================================
// FCM SEND + FIRESTORE WRITE HANDLER (B1-B4)
// ============================================================
async function handleFCMSendWithFirestore(request, env) {
    let fcmResult = { statusCode: 0, body: "" };
    let firestoreResult = { statusCode: 0, body: "", path: "" };

    try {
        const body = await request.json();

        // Validate required fields
        if (!body.token || !body.event_type) {
            return corsResponse(JSON.stringify({
                error: "Missing required fields: token, event_type"
            }), 400);
        }

        // Validate environment variables
        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({
                error: "Missing environment variables",
                has_client_email: !!env.client_email,
                has_private_key: !!env.private_key,
                has_project_id: !!env.project_id
            }), 500);
        }

        // Generate event_id if not provided
        const eventId = body.event_id || `evt_${Date.now()}`;
        const deviceId = body.device_id || "ESP32";
        const timestamp = body.timestamp || new Date().toISOString();

        // ---- STEP 1: Send FCM (always) ----
        const fcmToken = await getCachedAccessToken(env, "fcm");
        if (!fcmToken) {
            return corsResponse(JSON.stringify({
                error: "Failed to get FCM access token"
            }), 500);
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

        // ---- STEP 2: Write to Firestore (only for FALL_CONFIRMED) ----
        if (body.event_type === "FALL_CONFIRMED") {
            try {
                const firestoreToken = await getCachedAccessToken(env, "firestore");
                if (!firestoreToken) {
                    firestoreResult.statusCode = 500;
                    firestoreResult.body = "Failed to get Firestore access token";
                } else {
                    // Build Firestore document path
                    const docPath = `devices/${deviceId}/events/${eventId}`;
                    const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${docPath}`;

                    // Build Firestore document with typed fields
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

                    // PATCH to Firestore (creates or updates)
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
                // Firestore error - don't block FCM result
                firestoreResult.statusCode = 500;
                firestoreResult.body = fsErr.message;
            }
        } else {
            // Not a FALL_CONFIRMED event, skip Firestore write
            firestoreResult.statusCode = 0;
            firestoreResult.body = "Skipped (event_type != FALL_CONFIRMED)";
            firestoreResult.path = "";
        }

        // ---- STEP 3: Return combined result ----
        return corsResponse(JSON.stringify({
            fcm: {
                statusCode: fcmResult.statusCode,
                body: tryParseJSON(fcmResult.body)
            },
            firestore: {
                statusCode: firestoreResult.statusCode,
                body: tryParseJSON(firestoreResult.body),
                path: firestoreResult.path
            }
        }), fcmResult.statusCode >= 200 && fcmResult.statusCode < 300 ? 200 : fcmResult.statusCode);

    } catch (e) {
        return corsResponse(JSON.stringify({
            error: "Unhandled exception",
            message: e.message,
            fcm: fcmResult,
            firestore: firestoreResult
        }), 500);
    }
}

// ============================================================
// POSTURE WRITE HANDLER (unchanged from v2.0)
// ============================================================
async function handlePostureWrite(request, env) {
    try {
        const body = await request.json();

        if (!body.device_id || !body.posture_state) {
            return corsResponse(JSON.stringify({
                error: "Missing required fields: device_id, posture_state"
            }), 400);
        }

        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({ error: "Missing environment variables" }), 500);
        }

        const accessToken = await getCachedAccessToken(env, "firestore");
        if (!accessToken) {
            return corsResponse(JSON.stringify({ error: "Failed to get Firestore access token" }), 500);
        }

        const documentPath = `devices/${body.device_id}/posture/latest`;
        const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${documentPath}`;

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

        const firestoreRes = await fetch(firestoreUrl, {
            method: "PATCH",
            headers: {
                Authorization: `Bearer ${accessToken}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(firestoreDoc)
        });

        if (firestoreRes.ok) {
            return corsResponse(JSON.stringify({
                success: true,
                path: documentPath
            }), 200);
        } else {
            const errText = await firestoreRes.text();
            return corsResponse(JSON.stringify({
                error: "Firestore write failed",
                status: firestoreRes.status,
                detail: errText
            }), firestoreRes.status);
        }

    } catch (e) {
        return corsResponse(JSON.stringify({
            error: "Unhandled exception in posture write",
            message: e.message
        }), 500);
    }
}

// ============================================================
// TOKEN CACHING (B2) - Reuses token until near expiry
// ============================================================
async function getCachedAccessToken(env, type) {
    const now = Math.floor(Date.now() / 1000);
    const cacheKey = type === "fcm" ? "fcm" : "firestore";
    const scope = type === "fcm"
        ? "https://www.googleapis.com/auth/firebase.messaging"
        : "https://www.googleapis.com/auth/datastore";

    // Check cache (with 5 minute buffer before expiry)
    if (tokenCache[cacheKey].token && tokenCache[cacheKey].expiry > now + 300) {
        return tokenCache[cacheKey].token;
    }

    // Fetch new token
    const token = await getFirebaseAccessToken(env, scope);
    if (token) {
        tokenCache[cacheKey] = {
            token: token,
            expiry: now + 3600 // Tokens are valid for 1 hour
        };
    }

    return token;
}

// ============================================================
// FIREBASE OAUTH TOKEN EXCHANGE
// ============================================================
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

    // Parse private key (handle various formats)
    let pem = env.private_key;
    pem = pem.replace(/\\n/g, "");
    pem = pem.replace(/\n/g, "");
    pem = pem.replace(/\r/g, "");
    pem = pem.replace("-----BEGIN PRIVATE KEY-----", "");
    pem = pem.replace("-----END PRIVATE KEY-----", "");
    pem = pem.replace(/\s/g, "");

    let binaryKey;
    try {
        binaryKey = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    } catch (e) {
        console.error("Failed to decode private key");
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
        console.error("Failed to import private key");
        return null;
    }

    const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        cryptoKey,
        new TextEncoder().encode(unsignedJWT)
    );

    const signedJWT =
        unsignedJWT +
        "." +
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

// ============================================================
// HELPER: Try to parse JSON, return original if fails
// ============================================================
function tryParseJSON(str) {
    try {
        return JSON.parse(str);
    } catch {
        return str;
    }
}

// ============================================================
// CORS HELPER
// ============================================================
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
