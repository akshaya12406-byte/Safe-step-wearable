# SafeStep ESP32 Firmware

## Quick Start

### Hardware Requirements
- ESP32 DevKit V1 (or compatible)
- MPU6050 Accelerometer/Gyroscope Module
- 4 jumper wires

### Wiring Diagram

```
┌─────────────┐         ┌──────────────┐
│   ESP32     │         │   MPU6050    │
├─────────────┤         ├──────────────┤
│ 3.3V    ────┼─────────┼──── VCC      │
│ GND     ────┼─────────┼──── GND      │
│ GPIO21  ────┼─────────┼──── SDA      │
│ GPIO22  ────┼─────────┼──── SCL      │
└─────────────┘         └──────────────┘
```

### Configuration

Edit `safestep_fall_detector.ino`:

```cpp
// WiFi credentials
const char* WIFI_SSID = "YourWiFiName";
const char* WIFI_PASSWORD = "YourWiFiPassword";

// Cloudflare Worker URL (from deployment)
const char* WORKER_URL = "https://safestep-fcm-relay.your-name.workers.dev";

// FCM Token (get from Android app → Developer Mode)
const char* FCM_TOKEN = "your_fcm_token_here";

// Device ID (matches what Android app expects)
const char* DEVICE_ID = "ESP32_01";
```

### Upload to ESP32

1. Install Arduino IDE with ESP32 board support
2. Install libraries:
   - ArduinoJson (by Benoit Blanchon)
   - WiFi (built-in)
   - Wire (built-in)
3. Select board: "ESP32 Dev Module"
4. Set Upload Speed: 115200
5. Upload!

### LED Indicators

| Pattern | Meaning |
|---------|---------|
| 3 blinks on boot | System starting |
| Solid ON | Ready and monitoring |
| 2 quick blinks | Impact detected |
| 5 slow blinks | Alert sent successfully |
| 10 fast blinks | Alert failed |

### Serial Monitor Output

Open Serial Monitor at **115200 baud** to see:
- Sensor calibration
- Real-time fall detection
- Alert transmission status

### Fall Detection Parameters

Tune these in the code for your use case:

```cpp
// Impact threshold (typical fall: 2.5-4g)
const float IMPACT_THRESHOLD_G = 2.5;

// Post-fall stillness detection window
const unsigned long POST_IMPACT_WINDOW_MS = 2000;

// Cooldown between alerts
const unsigned long ALERT_COOLDOWN_MS = 30000;
```

### Architecture

```
Person falls
    ↓
MPU6050 detects high acceleration (>2.5g)
    ↓
ESP32 waits 2 seconds
    ↓
Checks if person is still (lying down)
    ↓
If confirmed: HTTPS POST to Cloudflare Worker
    ↓
Worker sends FCM to Android
    ↓
Phone shows full-screen alert
```

## Files

| File | Description |
|------|-------------|
| `safestep_fall_detector.ino` | **Main firmware** - use this |
| `send_fcm_example.ino` | Simple test sketch (no MPU6050) |
