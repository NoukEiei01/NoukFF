# 🛡️ VPN App — Android (Non-Root)

Android VPN app with floating overlay widget, built with Kotlin. No root required.  
Auto-builds APK via GitHub Actions CI/CD.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🔒 VPN Tunnel | Android native `VpnService` (no root) |
| 🪟 Floating Widget | Draggable overlay — connect/disconnect without opening the app |
| 🌍 Multi-Server | Server list with country flag, ping, load indicator |
| 📊 Live Stats | Upload/Download/Duration counter |
| 🔄 Auto-connect | Connect on device boot |
| ⚠️ Kill Switch | Block traffic if VPN drops |
| 🔧 Protocol Select | UDP / TCP / WireGuard |

---

## 📁 Project Structure

```
vpn-app/
├── app/src/main/
│   ├── java/com/vpnapp/
│   │   ├── model/          # VpnServer, VpnStatus, VpnStats
│   │   ├── service/
│   │   │   ├── MyVpnService.kt         # Core VPN tunnel
│   │   │   ├── FloatingWindowService.kt # Overlay widget
│   │   │   └── BootReceiver.kt
│   │   ├── ui/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ServerListActivity.kt
│   │   │   └── SettingsActivity.kt
│   │   └── util/
│   │       └── VpnStateManager.kt      # StateFlow state manager
│   └── res/
│       ├── layout/         # XML layouts
│       ├── drawable/       # Icons & status dots
│       └── xml/            # Preferences
├── .github/workflows/
│   └── build.yml           # GitHub Actions CI/CD
└── README.md
```

---

## 🚀 GitHub Actions CI/CD

### Automatic triggers:
- **Push to `main`** → builds release APK
- **Push tag `v*`** → builds + creates GitHub Release with APK download
- **Pull Request** → builds debug APK + runs tests
- **Manual trigger** → choose debug or release

### Setup Secrets (for signed release APK):

Go to your repo → **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i your-keystore.jks` (base64 encoded keystore) |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | Your key alias |
| `KEY_PASSWORD` | Your key password |

### Generate a keystore (first time):
```bash
keytool -genkey -v -keystore release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias vpnapp

# Encode to base64 for GitHub secret:
base64 -i release.jks | pbcopy   # macOS
base64 release.jks               # Linux
```

---

## 🛠️ Local Build

```bash
# Debug APK (no signing needed)
./gradlew assembleDebug

# Release APK (needs keystore env vars)
export KEYSTORE_FILE=release.jks
export KEYSTORE_PASSWORD=yourpass
export KEY_ALIAS=vpnapp
export KEY_PASSWORD=yourpass
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

---

## 📱 Permissions Required

| Permission | Reason |
|---|---|
| `BIND_VPN_SERVICE` | Create VPN tunnel (no root) |
| `FOREGROUND_SERVICE` | Keep VPN alive in background |
| `SYSTEM_ALERT_WINDOW` | Floating overlay widget |
| `RECEIVE_BOOT_COMPLETED` | Auto-connect on boot |
| `INTERNET` | Network access |

---

## ⚙️ Integrating a Real VPN Protocol

This project provides the full Android app shell. To add a real VPN backend:

### WireGuard (recommended):
```gradle
// Add to app/build.gradle:
implementation 'com.wireguard.android:tunnel:1.0.20230706'
```

### OpenVPN:
```gradle
implementation 'de.blinkt.openvpn:openvpn:0.7.39'
```

Replace the tunnel logic in `MyVpnService.kt` → `connect()` method with the library's tunnel creation.

---

## 📋 Requirements

- Android 6.0+ (API 23)
- Android Studio Hedgehog or later
- JDK 17
- **No root required**
