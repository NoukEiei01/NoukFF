# 📡 FakeLag — No-Root Android Lag Simulator

Simulate network lag, jitter, and packet loss on Android — **no root required**.  
Uses Android's built-in `VpnService` to delay packets at the OS level.

---

## ✨ Features

| Feature | Details |
|---|---|
| **Delay** | 0 – 2000 ms base latency |
| **Jitter** | ±0 – 500 ms random variation |
| **Packet Drop** | 0 – 90% drop rate |
| **Presets** | None / Low / Medium / High / Rage |
| **Live Update** | Change values while running — no restart |
| **Quick Settings Tile** | Toggle from notification shade |
| **Packet Stats** | Real-time in/out/dropped counters |
| **No Root** | Uses Android VpnService only |

---

## 🏗️ Build

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Local build
```bash
git clone https://github.com/YOUR_USERNAME/FakeLag.git
cd FakeLag
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions (CI/CD)

Every push to `main` or `develop` automatically builds a **debug APK**.  
Pushing a version tag builds and publishes a **signed release APK**.

```bash
git tag v1.0.0
git push origin v1.0.0
# → triggers release build + GitHub Release with APK attached
```

---

## 🔑 Setting up Release Signing (GitHub Secrets)

1. **Create a keystore** (one-time):
```bash
keytool -genkey -v \
  -keystore fakelag-release.keystore \
  -alias fakelag \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

2. **Encode to base64**:
```bash
base64 -w 0 fakelag-release.keystore > keystore.b64
```

3. **Add GitHub Secrets** (Settings → Secrets → Actions):

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `keystore.b64` |
| `KEY_STORE_PASSWORD` | Your store password |
| `KEY_ALIAS` | `fakelag` |
| `KEY_PASSWORD` | Your key password |

---

## 📱 Installation

1. Download the APK from [Releases](../../releases)
2. On your Android device: **Settings → Apps → Install unknown apps** → enable for your browser/Files app
3. Open the APK and install
4. Launch **FakeLag** → tap **START LAG** → grant VPN permission

---

## 🎮 How It Works

```
App ──► TUN (VPN Interface) ──► [DelayQueue with configured ms] ──► Real Network
                                        ↑
                              Jitter + Drop applied here
```

1. Android routes all traffic through our local VPN tunnel (TUN interface)
2. A reader thread captures raw IP packets
3. Each packet is inserted into a `DelayQueue` with `delay + random(±jitter)` ms
4. A writer thread drains the queue when packets are "ready" and forwards them
5. Optionally drops packets before forwarding (simulates real packet loss)

> **Note**: This delays all device traffic (not just games). Disable when done.

---

## 🛠️ Project Structure

```
FakeLag/
├── app/src/main/
│   ├── java/com/fakelag/app/
│   │   ├── model/LagConfig.java          # Config + presets + sampling
│   │   ├── service/FakeLagVpnService.java # Core VPN + DelayQueue engine
│   │   ├── service/FakeLagTileService.java # Quick Settings tile
│   │   └── ui/MainActivity.java          # UI + sliders + stats
│   └── res/
│       ├── layout/activity_main.xml      # Dark gaming UI
│       └── values/                       # Colors, themes, strings
├── .github/workflows/build.yml           # CI/CD pipeline
└── README.md
```

---

## ⚠️ Disclaimer

This tool is for **testing and development purposes** (e.g. testing your game's lag compensation, simulating bad network conditions for QA).  
Use responsibly. Don't use in online competitive games — that would be cheating.
