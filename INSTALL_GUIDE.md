# 📱 Installing Zoro App Store on Your Phone

## Prerequisites

1. **Enable Developer Options** on your phone:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable **USB Debugging**

2. **Install ADB** (if not already):
   ```powershell
   # Windows (via winget)
   winget install Google.AndroidSDKPlatformTools
   
   # Or download from: https://developer.android.com/studio/releases/platform-tools
   ```

## Quick Install (Automated)

### Option 1: PowerShell Script (Recommended)
```powershell
# Build AND install directly to connected phone
.\build-apk.ps1 -Install

# Or just build
.\build-apk.ps1
```

### Option 2: Batch File
```cmd
# Double-click or run:
build-apk.bat
# Then manually install:
adb install -r app-store-client\app\build\outputs\apk\release\app-release.apk
```

## Manual Build Steps

### 1. Configure API URL (Important!)
Create `app-store-client/local.properties`:
```properties
API_BASE_URL="http://YOUR_PC_IP:3000/api/v1/"
```
> **Find your PC IP**: Run `ipconfig` → look for IPv4 Address (e.g., `192.168.1.100`)

### 2. Build the APK
```powershell
cd app-store-client
.\gradlew.bat clean assembleRelease
```

### 3. Install on Phone
```powershell
# With phone connected via USB:
adb install -r app\build\outputs\apk\release\app-release.apk

# Or copy APK to phone and tap to install
```

## 🎨 App Icon Preview

The icon features:
- **Z Shape** as the main structure (white, bold)
- **Android Mascot (Bugdroid)** in **sitting pose** on the Z
  - Head resting on top-left of Z
  - Body sitting on the diagonal stroke
  - Arms resting on Z's arms
  - Legs folded (sitting)
  - Green color (#A4C639) with white eyes
- **Deep Purple Background** (#4445E8) matching Material3 theme
- **Adaptive Icon Support** - works with Android 8+ themed icons

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| `adb: device not found` | Enable USB Debugging, try `adb kill-server && adb start-server` |
| `INSTALL_FAILED_TEST_ONLY` | Add `-t` flag: `adb install -r -t app-release.apk` |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Uninstall old version first: `adb uninstall com.zoroapps.appstore` |
| App crashes on launch | Check logcat: `adb logcat | findstr ZoroAppStore` |
| Can't connect to server | Verify `local.properties` IP matches your PC's LAN IP |

## 🚀 First Run

1. **Grant Permissions** when prompted:
   - Install unknown apps (for APK installation)
   - Notifications (for download/update alerts)
   - Storage (for downloads)

2. **Configure Backend** (if not auto-detected):
   - Open app → Settings → Server URL
   - Enter: `http://YOUR_PC_IP:3000/api/v1/`

3. **Add Your Apps** via Admin API:
   ```bash
   # Upload APK via server API or use admin panel
   ```

## 📦 Release Build Details

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: arm64-v8a, armeabi-v7a, x86, x86_64
- **Size**: ~15-20 MB (optimized with R8)
- **Signing**: Debug keystore (create `keystore/release.keystore` for production)

---

**Need help?** Run `adb logcat -s ZoroAppStore:*` to see debug logs.