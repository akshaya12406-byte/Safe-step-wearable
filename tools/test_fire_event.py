#!/usr/bin/env python3
"""
SafeStep Test Harness - Event Injection Script

This script sends fall alerts to the Cloudflare Worker relay,
which forwards them to the Android app via FCM HTTP v1.

USAGE:
  python test_fire_event.py --worker     # Send via Cloudflare Worker
  python test_fire_event.py --firestore  # Insert Firestore event only

SETUP:
1. Replace WORKER_URL with your Cloudflare Worker URL
2. Run: python test_fire_event.py --worker

Author: SafeStep Team
"""

import argparse
import json
import requests
from datetime import datetime
import random
import string

# ============== CONFIGURATION ==============
# PASTE YOUR CLOUDFLARE WORKER URL HERE ↓
WORKER_URL = "https://safestep-fcm.YOUR-SUBDOMAIN.workers.dev"

# Test device ID (must match what's in Firestore)
DEVICE_ID = "ESP32_TEST"
# ===========================================


def generate_event_id():
    """Generate a random event ID."""
    suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return f"evt_{suffix}"


def send_via_worker(event_type="FALL_CONFIRMED", impact_g=3.05, pitch=12.4, roll=5.1):
    """Send fall alert via Cloudflare Worker."""
    
    if "YOUR-SUBDOMAIN" in WORKER_URL:
        print("❌ Error: Please set WORKER_URL in the script")
        print("   Open test_fire_event.py and update the WORKER_URL constant")
        return False
    
    payload = {
        "device_id": DEVICE_ID,
        "event_type": event_type,
        "event_id": generate_event_id(),
        "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "impact_g": impact_g,
        "pitch": pitch,
        "roll": roll
    }
    
    print(f"📤 Sending to Cloudflare Worker...")
    print(f"   URL: {WORKER_URL}")
    print(f"   Payload: {json.dumps(payload, indent=2)}")
    
    try:
        response = requests.post(WORKER_URL, json=payload, timeout=10)
        
        print(f"\n📬 Response Status: {response.status_code}")
        print(f"   Response Body: {response.text}")
        
        if response.status_code == 200:
            print("\n✅ Alert sent successfully!")
            print("   Check your Android device for the full-screen alert.")
            return True
        else:
            print(f"\n⚠️ Worker returned status {response.status_code}")
            return False
            
    except requests.exceptions.ConnectionError:
        print("\n❌ Connection Error: Cannot reach the worker")
        print("   Check if the URL is correct")
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
    parser.add_argument("--worker", action="store_true", help="Send via Cloudflare Worker (triggers FCM)")
    parser.add_argument("--firestore", action="store_true", help="Insert Firestore event only")
    parser.add_argument("--both", action="store_true", help="Send via worker AND insert Firestore event")
    parser.add_argument("--event-type", default="FALL_CONFIRMED", help="Event type (default: FALL_CONFIRMED)")
    parser.add_argument("--impact", type=float, default=3.05, help="Impact in g (default: 3.05)")
    parser.add_argument("--pitch", type=float, default=12.4, help="Pitch angle (default: 12.4)")
    parser.add_argument("--roll", type=float, default=5.1, help="Roll angle (default: 5.1)")
    
    args = parser.parse_args()
    
    if not any([args.worker, args.firestore, args.both]):
        parser.print_help()
        print("\n" + "=" * 50)
        print("💡 Quick Start:")
        print("   1. Edit WORKER_URL in this file")
        print("   2. Run: python test_fire_event.py --worker")
        print("=" * 50)
        return
    
    print("=" * 50)
    print("  SafeStep Test Harness")
    print("=" * 50)
    print()
    
    if args.worker or args.both:
        send_via_worker(args.event_type, args.impact, args.pitch, args.roll)
        print()
    
    if args.firestore or args.both:
        insert_firestore_event(args.event_type, args.impact, args.pitch, args.roll)


if __name__ == "__main__":
    main()
