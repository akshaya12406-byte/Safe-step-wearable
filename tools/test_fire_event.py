#!/usr/bin/env python3
"""
SafeStep Test Harness - Event Injection Script

This script can:
1. Send FCM messages directly to trigger app notifications
2. Insert sample events into Firestore for testing

USAGE:
  python test_fire_event.py --fcm           # Send FCM notification
  python test_fire_event.py --firestore     # Insert Firestore event
  python test_fire_event.py --both          # Both

SETUP:
1. Replace FCM_SERVER_KEY with your Firebase Cloud Messaging server key
2. For Firestore: Install firebase-admin and set GOOGLE_APPLICATION_CREDENTIALS
3. Run: python test_fire_event.py --fcm

Author: SafeStep Team
"""

import argparse
import json
import requests
from datetime import datetime
import random
import string

# ============== CONFIGURATION ==============
# FCM Server Key - Get from Firebase Console → Cloud Messaging
FCM_SERVER_KEY = "YOUR_FCM_SERVER_KEY_HERE"

# FCM endpoint
FCM_URL = "https://fcm.googleapis.com/fcm/send"

# Target: topic or device token
USE_TOPIC = True
FCM_TOPIC = "caregiver"
DEVICE_TOKEN = "YOUR_DEVICE_TOKEN_HERE"  # Only if USE_TOPIC is False

# Test device ID
DEVICE_ID = "ESP32_TEST"
# ===========================================


def generate_event_id():
    """Generate a random event ID."""
    suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return f"evt_{suffix}"


def send_fcm_notification(event_type="FALL_CONFIRMED", impact_g=3.05, pitch=12.4, roll=5.1):
    """Send FCM notification to trigger app alert."""
    
    if FCM_SERVER_KEY == "YOUR_FCM_SERVER_KEY_HERE":
        print("❌ Error: Please set FCM_SERVER_KEY in the script")
        return False
    
    # Build payload
    payload = {
        "priority": "high",
        "data": {
            "event_type": event_type,
            "device_id": DEVICE_ID,
            "event_id": generate_event_id(),
            "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
            "impact_g": str(impact_g),
            "pitch": str(pitch),
            "roll": str(roll)
        }
    }
    
    if USE_TOPIC:
        payload["to"] = f"/topics/{FCM_TOPIC}"
    else:
        payload["to"] = DEVICE_TOKEN
    
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"key={FCM_SERVER_KEY}"
    }
    
    print(f"📤 Sending FCM message...")
    print(f"   Target: {payload['to']}")
    print(f"   Event Type: {event_type}")
    print(f"   Impact: {impact_g}g")
    print(f"   Payload: {json.dumps(payload, indent=2)}")
    
    try:
        response = requests.post(FCM_URL, json=payload, headers=headers)
        
        print(f"\n📬 Response Status: {response.status_code}")
        print(f"   Response Body: {response.text}")
        
        if response.status_code == 200:
            result = response.json()
            if result.get("success", 0) >= 1:
                print("\n✅ FCM message sent successfully!")
                print("   Check your Android device for the full-screen alert.")
                return True
            else:
                print("\n⚠️ Message sent but delivery failed.")
                print("   Check if device token is correct or topic subscription exists.")
                return False
        elif response.status_code == 401:
            print("\n❌ Authentication failed. Check your FCM_SERVER_KEY.")
            return False
        else:
            print(f"\n❌ Failed with status {response.status_code}")
            return False
            
    except Exception as e:
        print(f"\n❌ Error: {e}")
        return False


def insert_firestore_event(event_type="FALL_CONFIRMED", impact_g=3.05, pitch=12.4, roll=5.1):
    """Insert a sample event into Firestore."""
    
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore
    except ImportError:
        print("❌ Error: firebase-admin not installed")
        print("   Run: pip install firebase-admin")
        print("   Then set GOOGLE_APPLICATION_CREDENTIALS environment variable")
        return False
    
    # Initialize Firebase Admin if not already done
    if not firebase_admin._apps:
        try:
            cred = credentials.ApplicationDefault()
            firebase_admin.initialize_app(cred)
        except Exception as e:
            print(f"❌ Firebase init error: {e}")
            print("   Set GOOGLE_APPLICATION_CREDENTIALS to your service account JSON")
            return False
    
    db = firestore.client()
    
    event_id = generate_event_id()
    event_data = {
        "event_type": event_type,
        "device_id": DEVICE_ID,
        "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "impact_g": impact_g,
        "pitch": pitch,
        "roll": roll,
        "handled": False,
        "acknowledged_by": None,
        "firmware_version": "1.0.0"
    }
    
    print(f"📝 Inserting Firestore event...")
    print(f"   Path: devices/{DEVICE_ID}/events/{event_id}")
    print(f"   Data: {json.dumps(event_data, indent=2)}")
    
    try:
        doc_ref = db.collection("devices").document(DEVICE_ID).collection("events").document(event_id)
        doc_ref.set(event_data)
        print(f"\n✅ Event inserted successfully!")
        print(f"   Event ID: {event_id}")
        return True
    except Exception as e:
        print(f"\n❌ Firestore error: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="SafeStep Test Harness")
    parser.add_argument("--fcm", action="store_true", help="Send FCM notification")
    parser.add_argument("--firestore", action="store_true", help="Insert Firestore event")
    parser.add_argument("--both", action="store_true", help="Send FCM and insert Firestore event")
    parser.add_argument("--event-type", default="FALL_CONFIRMED", help="Event type (default: FALL_CONFIRMED)")
    parser.add_argument("--impact", type=float, default=3.05, help="Impact in g (default: 3.05)")
    parser.add_argument("--pitch", type=float, default=12.4, help="Pitch angle (default: 12.4)")
    parser.add_argument("--roll", type=float, default=5.1, help="Roll angle (default: 5.1)")
    
    args = parser.parse_args()
    
    if not any([args.fcm, args.firestore, args.both]):
        parser.print_help()
        print("\n💡 Example: python test_fire_event.py --fcm")
        return
    
    print("=" * 50)
    print("  SafeStep Test Harness")
    print("=" * 50)
    print()
    
    if args.fcm or args.both:
        send_fcm_notification(args.event_type, args.impact, args.pitch, args.roll)
        print()
    
    if args.firestore or args.both:
        insert_firestore_event(args.event_type, args.impact, args.pitch, args.roll)


if __name__ == "__main__":
    main()
