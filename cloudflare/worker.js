// SafeStep FCM Relay Worker - Robust Version
// Deploy this to Cloudflare Workers

export default {
    async fetch(request, env) {
        // CORS headers for preflight
        if (request.method === "OPTIONS") {
            return new Response(null, {
                headers: {
                    "Access-Control-Allow-Origin": "*",
                    "Access-Control-Allow-Methods": "POST, OPTIONS",
                    "Access-Control-Allow-Headers": "Content-Type",
                },
            });
        }

        if (request.method !== "POST") {
            return new Response("Only POST allowed", { status: 405 });
        }

        try {
            const body = await request.json();

            if (!body.token || !body.event_type) {
                return new Response(JSON.stringify({ error: "Missing token or event_type" }), {
                    status: 400,
                    headers: { "Content-Type": "application/json" }
                });
            }

            // Validate environment variables exist
            if (!env.client_email || !env.private_key || !env.project_id) {
                return new Response(JSON.stringify({
                    error: "Missing environment variables",
                    has_client_email: !!env.client_email,
                    has_private_key: !!env.private_key,
                    has_project_id: !!env.project_id
                }), {
                    status: 500,
                    headers: { "Content-Type": "application/json" }
                });
            }

            // ---- JWT BUILD ----
            const now = Math.floor(Date.now() / 1000);
            const header = { alg: "RS256", typ: "JWT" };
            const claim = {
                iss: env.client_email,
                scope: "https://www.googleapis.com/auth/firebase.messaging",
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

            // ---- ROBUST PRIVATE KEY HANDLING ----
            // Handle all possible formats:
            // - Literal \n as text (from JSON paste)
            // - Actual newlines
            // - With or without PEM headers
            // - Extra whitespace
            let pem = env.private_key;

            // Replace literal \n (backslash + n) with nothing
            pem = pem.replace(/\\n/g, "");

            // Replace actual newlines
            pem = pem.replace(/\n/g, "");
            pem = pem.replace(/\r/g, "");

            // Remove PEM headers
            pem = pem.replace("-----BEGIN PRIVATE KEY-----", "");
            pem = pem.replace("-----END PRIVATE KEY-----", "");

            // Remove any remaining whitespace
            pem = pem.replace(/\s/g, "");

            // Decode base64 to binary
            let binaryKey;
            try {
                binaryKey = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
            } catch (e) {
                return new Response(JSON.stringify({
                    error: "Failed to decode private key base64",
                    detail: e.message,
                    pem_length: pem.length,
                    pem_preview: pem.substring(0, 20) + "..."
                }), {
                    status: 500,
                    headers: { "Content-Type": "application/json" }
                });
            }

            // Import the key
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
                return new Response(JSON.stringify({
                    error: "Failed to import private key",
                    detail: e.message
                }), {
                    status: 500,
                    headers: { "Content-Type": "application/json" }
                });
            }

            // Sign the JWT
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

            // ---- OAUTH TOKEN ----
            const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJWT}`,
            });

            const tokenData = await tokenRes.json();
            if (!tokenData.access_token) {
                return new Response(JSON.stringify({
                    error: "OAuth token exchange failed",
                    oauth_response: tokenData
                }), {
                    status: 500,
                    headers: { "Content-Type": "application/json" }
                });
            }

            // ---- SEND FCM ----
            const fcmRes = await fetch(
                `https://fcm.googleapis.com/v1/projects/${env.project_id}/messages:send`,
                {
                    method: "POST",
                    headers: {
                        Authorization: `Bearer ${tokenData.access_token}`,
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
                            },
                        },
                    }),
                }
            );

            const result = await fcmRes.text();
            return new Response(result, {
                status: fcmRes.status,
                headers: { "Content-Type": "application/json" }
            });

        } catch (e) {
            // Catch-all error handler
            return new Response(JSON.stringify({
                error: "Unhandled exception",
                message: e.message,
                stack: e.stack
            }), {
                status: 500,
                headers: { "Content-Type": "application/json" }
            });
        }
    },
};
