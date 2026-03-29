# D2D Remote — Device-to-Device Remote Control

A native Android app that allows one device (Controller) to view and control another device's (Target) screen in real-time over the local WiFi network.

## Features

- **Real-time screen sharing** via MediaProjection + H.264 encoding
- **Touch injection** using Android AccessibilityService
- **Low-latency TCP streaming** with optimized buffer management
- **Modern Material 3 UI** with Jetpack Compose, animations, and clean design
- **MVVM architecture** for maintainable, testable code
- **Robust error handling** — graceful reconnection on WiFi drops

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 35 (compile SDK)
- Android 10+ (API 29) on both devices
- Both devices on the same WiFi network
- JDK 17

## Build Instructions

### 1. Clone or download the project

Copy the `android-d2d-remote` folder to your local machine.

### 2. Regenerate the Gradle wrapper (recommended)

If you encounter issues with the bundled wrapper, regenerate it by running:

```bash
gradle wrapper --gradle-version 8.9
```

This requires Gradle to be installed on your system. Alternatively, open the project in Android Studio and it will handle the wrapper automatically.

### 3. Open in Android Studio

Open the `android-d2d-remote` folder as an existing project in Android Studio. Android Studio will sync the project and download all required dependencies.

### 4. Build the APK

From the project root directory:

```bash
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 5. Install on both devices

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or install directly from Android Studio by selecting a connected device and clicking Run.

## How to Use

### Setup the Target Device (the device being controlled)

1. **Install the app** on the Target device
2. **Enable the Accessibility Service:**
   - Open the D2D Remote app
   - Tap **"Be Target"**
   - Tap **"Enable Accessibility Service"** — this opens Android Settings
   - Navigate to: **Settings → Accessibility → D2D Remote Touch**
   - Toggle the service **ON**
   - Confirm the permission dialog
   - Return to the D2D Remote app
3. **Note the IP address** and **Pairing Code** displayed on the Target screen
4. Tap **"Start Server"** — the app will request screen capture permission
5. Grant the screen capture permission

### Setup the Controller Device (the device doing the controlling)

1. **Install the app** on the Controller device
2. Open the app and tap **"Be Controller"**
3. **Enter the Target's IP address** (shown on the Target device)
4. **Enter the 6-digit Pairing Code** (shown on the Target device)
5. Tap **"Connect"**
6. Once connected, you'll see the Target's screen and can interact with it by touching the video area

### Security

Both video and control connections require a **6-digit pairing code** before accepting clients. The Target device generates a random code that must be entered on the Controller device. This provides basic access control on the local network but does **not** encrypt traffic. The pairing code prevents casual unauthorized connections from other devices on the same WiFi network. For sensitive use cases, consider running the app on a private/isolated network.

### Stopping the Session

- **Target:** Tap the notification or return to the app and tap "Stop Server"
- **Controller:** Tap the back button or "Disconnect"

## Architecture

```
com.d2dremote/
├── model/                  # Data classes (TouchEvent, ConnectionState, ScreenInfo)
├── network/                # Socket communication layer
│   ├── NetworkUtils         # IP discovery, port constants
│   ├── VideoStreamServer    # TCP server for encoded video frames
│   ├── VideoStreamClient    # TCP client for receiving video
│   ├── ControlServer        # TCP server for touch event reception
│   └── ControlClient        # TCP client for sending touch events
├── service/                # Android services
│   ├── ScreenCaptureService # Foreground service with MediaProjection
│   ├── VideoEncoder         # H.264 encoding via MediaCodec
│   ├── VideoDecoder         # H.264 decoding via MediaCodec
│   └── TouchAccessibilityService  # Gesture injection via AccessibilityService
├── ui/                     # Jetpack Compose UI layer
│   ├── theme/              # Material 3 theme (colors, typography)
│   ├── components/         # Reusable UI components
│   ├── home/               # Home screen (role selection)
│   ├── target/             # Target mode (server) screen + ViewModel
│   └── controller/         # Controller mode screen + ViewModel
└── navigation/             # Navigation graph
```

### Communication Protocol

- **Video Stream (Port 9100):** TCP connection. Each frame is preceded by a 4-byte size header (big-endian int). Negative sizes indicate metadata (screen info).
- **Control Socket (Port 9101):** TCP connection. Touch events are sent as JSON-encoded lines with type (DOWN/MOVE/UP) and coordinates.

## Network Ports

| Port | Purpose |
|------|---------|
| 9100 | Video stream (H.264 frames) |
| 9101 | Control channel (touch events) |

## Permissions

| Permission | Purpose |
|------------|---------|
| INTERNET | Network communication |
| ACCESS_NETWORK_STATE | Network status checks |
| ACCESS_WIFI_STATE | WiFi IP discovery |
| FOREGROUND_SERVICE | Screen capture service |
| FOREGROUND_SERVICE_MEDIA_PROJECTION | Media projection type |
| SYSTEM_ALERT_WINDOW | Overlay permissions |
| POST_NOTIFICATIONS | Capture notification |

## Troubleshooting

- **Can't connect?** Make sure both devices are on the same WiFi network and no firewall is blocking ports 9100-9101.
- **Accessibility Service not showing?** Reinstall the app and check Settings → Accessibility.
- **Video lag?** The encoder uses 2 Mbps bitrate. You can adjust `BIT_RATE` in `VideoEncoder.kt` for your network.
- **App crashes on connection loss?** All socket operations have error handling with graceful fallbacks. If you still see crashes, please file an issue.

## License

MIT License
