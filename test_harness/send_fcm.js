/**
 * SafeStep Test Harness - Direct FCM Testing
 * 
 * This script sends FCM messages directly to your device/topic
 * using the FCM Legacy HTTP API (works with Spark plan).
 * 
 * SETUP:
 * 1. Get your FCM Server Key from Firebase Console → Project Settings → Cloud Messaging
 * 2. Replace FCM_SERVER_KEY below
 * 3. Get your device FCM token from the app (displayed on MainActivity)
 * 4. Replace DEVICE_TOKEN below (or use topic)
 * 5. Run: node send_fcm.js
 */

const https = require('https');

// ============== CONFIGURATION ==============
const FCM_SERVER_KEY = 'YOUR_FCM_SERVER_KEY_HERE'; // From Firebase Console
const DEVICE_TOKEN = 'YOUR_DEVICE_TOKEN_HERE';     // From app logs or use topic below
const USE_TOPIC = true;                             // Set to true to send to topic instead
const TOPIC = 'caregiver';                          // Topic name
// ===========================================

const payload = {
    to: USE_TOPIC ? `/topics/${TOPIC}` : DEVICE_TOKEN,
    priority: 'high',
    data: {
        type: 'FALL_ALERT',
        event_type: 'FALL_CONFIRMED',
        deviceId: 'ESP32_TEST',
        device_id: 'ESP32_TEST',
        timestamp: new Date().toISOString(),
        impact_g: '3.5',
        eventId: `evt_${Date.now()}`
    }
};

const options = {
    hostname: 'fcm.googleapis.com',
    path: '/fcm/send',
    method: 'POST',
    headers: {
        'Authorization': `key=${FCM_SERVER_KEY}`,
        'Content-Type': 'application/json'
    }
};

console.log('📤 Sending FCM message...');
console.log('Payload:', JSON.stringify(payload, null, 2));

const req = https.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => data += chunk);
    res.on('end', () => {
        console.log(`\n📬 Response Status: ${res.statusCode}`);
        console.log('Response:', data);

        if (res.statusCode === 200) {
            const response = JSON.parse(data);
            if (response.success === 1) {
                console.log('\n✅ FCM message sent successfully!');
                console.log('Check your Android device for the full-screen alert.');
            } else {
                console.log('\n⚠️ Message sent but delivery may have failed.');
                console.log('Check if device token is correct or topic subscription exists.');
            }
        } else if (res.statusCode === 401) {
            console.log('\n❌ Authentication failed. Check your FCM_SERVER_KEY.');
        } else {
            console.log('\n❌ Failed to send FCM message.');
        }
    });
});

req.on('error', (e) => {
    console.error('❌ Error:', e.message);
});

req.write(JSON.stringify(payload));
req.end();
