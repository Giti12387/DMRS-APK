# Zoro App Store

A professional Android app store for distributing your own apps with Play Store-like installation experience, including signature verification bypass for your own apps.

## Features

- 🎨 **Professional Material3 UI** - Modern, beautiful interface following Google Play Store design guidelines
- 📦 **App Management** - Browse, search, install, and update your apps
- 🔒 **Signature Handling** - Install apps even with signature mismatches (for your own apps)
- ⬇️ **Background Downloads** - Resumeable downloads with progress notifications
- 🔄 **Auto Updates** - Automatic update checking with notifications
- 🛡️ **Security** - Biometric authentication for installations
- 📊 **Analytics** - Download stats, ratings, and reviews
- 🏪 **Developer Portal** - Upload and manage your apps

## Architecture

```
├── app-store-client/          # Android App Store Client (Kotlin + Jetpack Compose)
│   ├── app/                   # Main application module
│   │   ├── src/main/
│   │   │   ├── java/com/zoroapps/appstore/
│   │   │   │   ├── data/      # Data layer (Repository, Room, Retrofit)
│   │   │   │   ├── di/        # Hilt Dependency Injection
│   │   │   │   ├── service/   # Download & Install Services
│   │   │   │   ├── ui/        # Compose UI Screens
│   │   │   │   ├── utils/     # Utilities (APK verification, installation)
│   │   │   │   └── ZoroAppStoreApplication.kt
│   │   │   └── res/           # Resources (themes, strings, layouts)
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── app-store-server/          # Backend Server (Node.js + TypeScript + Express)
│   ├── src/
│   │   ├── routes/            # API Routes (apps, categories, upload, stats)
│   │   ├── middleware/        # Auth, validation, error handling
│   │   ├── utils/             # Database, helpers
│   │   └── index.ts           # Entry point
│   ├── Dockerfile
│   ├── package.json
│   └── tsconfig.json
│
└── docker-compose.yml         # Docker deployment
```

## Requirements

### Android Client
- Android Studio Hedgehog | 2023.1.1 or later
- JDK 17
- Android SDK 34
- Minimum SDK: 24 (Android 7.0)

### Backend Server
- Node.js 20+
- SQLite3 (included via better-sqlite3)

## Quick Start

### 1. Backend Server

```bash
cd app-store-server
cp .env.example .env
# Edit .env with your configuration
npm install
npm run dev
```

Server runs at `http://localhost:3000`

### 2. Android Client

Open `app-store-client` in Android Studio and build/run.

**Important**: Configure the API base URL in `local.properties` or `build.gradle.kts`:
```properties
API_BASE_URL="http://10.0.2.2:3000/api/v1/"  # For emulator
# API_BASE_URL="http://YOUR_SERVER_IP:3000/api/v1/"  # For device
```

### 3. Docker Deployment (Production)

```bash
# Set environment variables
export JWT_SECRET="your-super-secret-jwt-key"
export CORS_ORIGIN="https://yourdomain.com"

docker-compose up -d --build
```

## Key Features Implementation

### Signature Verification Bypass

The app store can install your apps even with signature mismatches:

```kotlin
// In InstallService.kt
val bypassSignature = true // For your own apps
installViaPackageInstaller(file, packageName, versionCode, bypassSignature)
```

The `ApkUtils.verifyApk()` function verifies APK integrity while allowing bypass for trusted apps.

### Background Downloads

Downloads use `WorkManager` and `Foreground Service` for reliable background downloads:

```kotlin
// Start download
val intent = Intent(context, DownloadService::class.java).apply {
    action = DownloadService.ACTION_START
    putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
}
context.startForegroundService(intent)
```

### Auto Updates

Periodic update checks using `WorkManager`:

```kotlin
// UpdateCheckWorker runs every 6 hours
val workRequest = PeriodicWorkRequest.Builder(
    UpdateCheckWorker::class.java,
    6, TimeUnit.HOURS
).build()
```

### Biometric Authentication

Optional biometric prompt before installation:

```kotlin
// In Settings
prefs.biometricAuthEnabled = true

// Before install
if (prefs.biometricAuthEnabled) {
    BiometricPrompt.authenticate { success ->
        if (success) installApp()
    }
}
```

## API Endpoints

### Apps
- `GET /api/v1/apps` - List apps (pagination, filters)
- `GET /api/v1/apps/featured` - Featured apps
- `GET /api/v1/apps/new` - New releases
- `GET /api/v1/apps/top-charts` - Top downloads
- `GET /api/v1/apps/:id` - App details
- `GET /api/v1/apps/:id/download` - Get download URL
- `POST /api/v1/apps` - Create app (admin)
- `PATCH /api/v1/apps/:id` - Update app (admin)
- `DELETE /api/v1/apps/:id` - Delete app (admin)

### Categories
- `GET /api/v1/categories` - List categories
- `POST /api/v1/categories` - Create category (admin)

### Upload
- `POST /api/v1/upload/apk` - Upload APK
- `POST /api/v1/upload/icon` - Upload app icon
- `POST /api/v1/upload/banner` - Upload banner
- `POST /api/v1/upload/screenshots` - Upload screenshots

### Stats
- `GET /api/v1/stats` - Overall statistics
- `GET /api/v1/stats/app/:appId` - App-specific stats

## Security Considerations

1. **Change JWT Secret** - Use a strong random string in production
2. **HTTPS Only** - Use nginx reverse proxy with SSL certificates
3. **App Verification** - Only allow trusted developers to upload
4. **Signature Verification** - Enable for production, bypass only for testing
5. **Rate Limiting** - Configure rate limiting for API endpoints

## Customization

### Theming
Modify `app-store-client/app/src/main/java/com/zoroapps/appstore/ui/theme/Theme.kt` for custom colors, typography, and shapes.

### Categories
Add/remove categories in `database.ts` initialization or via admin API.

### Permissions
The app requests these permissions:
- `INTERNET` - Network access
- `REQUEST_INSTALL_PACKAGES` - Install APKs
- `FOREGROUND_SERVICE` - Background downloads
- `POST_NOTIFICATIONS` - Download/update notifications
- `USE_BIOMETRIC` - Biometric authentication

## Building Release APK

```bash
cd app-store-client
./gradlew assembleRelease
```

The signed APK will be in `app/build/outputs/apk/release/`

## License

MIT License - Feel free to use for your own app distribution.

## Support

For issues and feature requests, please create an issue in the repository.