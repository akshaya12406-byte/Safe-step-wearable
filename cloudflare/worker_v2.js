// SafeStep FCM Relay Worker - v2.0 with Posture Endpoint
// Deploy to Cloudflare Workers
//
// Environment Variables Required:
// - client_email: Firebase service account email
// - private_key: Firebase service account private key
// - project_id: Firebase project ID

export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        // CORS headers for preflight
        if (request.method === "OPTIONS") {
            return corsResponse(null, 204);
        }

        // Route handling
        if (request.method === "POST") {
            if (url.pathname === "/writePosture" || url.pathname === "/writeposture") {
                return handlePostureWrite(request, env);
            } else {
                // Default: FCM send (root path)
                return handleFCMSend(request, env);
            }
        }

        if (request.method === "GET" && url.pathname === "/health") {
            return corsResponse(JSON.stringify({ status: "ok", version: "2.0" }), 200);
        }

        return corsResponse("Only POST allowed. Endpoints: / (FCM), /writePosture", 405);
    },
};

// ============================================================
// FCM SEND HANDLER (existing functionality)
// ============================================================
async function handleFCMSend(request, env) {
    try {
        const body = await request.json();

        if (!body.token || !body.event_type) {
            return corsResponse(JSON.stringify({ error: "Missing token or event_type" }), 400);
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

        // Get OAuth access token
        const accessToken = await getFirebaseAccessToken(env, "https://www.googleapis.com/auth/firebase.messaging");
        if (!accessToken) {
            return corsResponse(JSON.stringify({ error: "Failed to get access token" }), 500);
        }

        // Send FCM
        const fcmRes = await fetch(
            `https://fcm.googleapis.com/v1/projects/${env.project_id}/messages:send`,
            {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${accessToken}`,
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    message: {
                        token: body.token,
                        android: { priority: "HIGH" },
                        data: {
                            event_type: body.event_type,
                            device_id: body.device_id || "ESP32",
                            timestamp: new Date().toISOString(),
                            impact_g: body.impact_g || "",
                            pitch: body.pitch || "",
                            roll: body.roll || "",
                            event_id: body.event_id || `evt_${Date.now()}`,
                        },
                    },
                }),
            }
        );

        const result = await fcmRes.text();
        return corsResponse(result, fcmRes.status);

    } catch (e) {
        return corsResponse(JSON.stringify({
            error: "Unhandled exception",
            message: e.message,
        }), 500);
    }
}

// ============================================================
// POSTURE WRITE HANDLER (B1 Implementation)
// ============================================================
async function handlePostureWrite(request, env) {
    try {
        const body = await request.json();

        // Validate required fields
        if (!body.device_id || !body.posture_state) {
            return corsResponse(JSON.stringify({
                error: "Missing required fields: device_id, posture_state"
            }), 400);
        }

        // Validate env
        if (!env.client_email || !env.private_key || !env.project_id) {
            return corsResponse(JSON.stringify({ error: "Missing environment variables" }), 500);
        }

        // Get OAuth access token with Firestore scope
        const accessToken = await getFirebaseAccessToken(env, "https://www.googleapis.com/auth/datastore");
        if (!accessToken) {
            return corsResponse(JSON.stringify({ error: "Failed to get Firestore access token" }), 500);
        }

        // Build Firestore document path
        // Path: devices/{device_id}/posture/latest
        const documentPath = `devices/${body.device_id}/posture/latest`;
        const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${env.project_id}/databases/(default)/documents/${documentPath}`;

        // Build Firestore document
        const firestoreDoc = {
            fields: {
                device_id: { stringValue: body.device_id },
                posture_state: { stringValue: body.posture_state },
                pitch: { doubleValue: parseFloat(body.pitch) || 0 },
                roll: { doubleValue: parseFloat(body.roll) || 0 },
                timestamp: { timestampValue: body.timestamp || new Date().toISOString() },
                updated_at: { timestampValue: new Date().toISOString() },
            }
        };

        // PATCH to Firestore (upsert)
        const firestoreRes = await fetch(firestoreUrl, {
            method: "PATCH",
            headers: {
                Authorization: `Bearer ${accessToken}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify(firestoreDoc),
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
            message: e.message,
        }), 500);
    }
}

// ============================================================
// SHARED: Firebase OAuth Token Exchange
// ============================================================
async function getFirebaseAccessToken(env, scope) {
    const now = Math.floor(Date.now() / 1000);
    const header = { alg: "RS256", typ: "JWT" };
    const claim = {
        iss: env.client_email,
        scope: scope,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
    };

    const base64url = (obj) =>
        btoa(JSON.stringify(obj))
            .replace(/\+/g, "-")
            .replace(/\//g, "_")
            .replace(/=+$/, "");

    const unsignedJWT = `${base64url(header)}.${base64url(claim)}`;

    // Parse private key
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
        console.error("Failed to decode private key:", e);
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
        console.error("Failed to import private key:", e);
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
        body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJWT}`,
    });

    const tokenData = await tokenRes.json();
    return tokenData.access_token || null;
}

// ============================================================
// CORS Helper
// ============================================================
function corsResponse(body, status) {
    return new Response(body, {
        status: status,
        headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type",
        },
    });
}
