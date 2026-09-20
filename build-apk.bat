@echo off
chcp 65001 >nul
title Building Zoro App Store APK

echo ==========================================
echo   Building Zoro App Store - Release APK
echo ==========================================
echo.

cd /d "%~dp0\app-store-client"

echo Checking Gradle wrapper...
if not exist gradlew (
    echo Generating Gradle wrapper...
    gradle wrapper --gradle-version 8.5
)

echo.
echo Building Release APK...
echo This may take a few minutes on first run...
echo.

call gradlew.bat clean assembleRelease

if %errorlevel% neq 0 (
    echo.
    echo ==========================================
    echo   BUILD FAILED!
    echo ==========================================
    echo Check the error messages above.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   BUILD SUCCESSFUL!
echo ==========================================
echo.

echo APK Location:
dir /b app\build\outputs\apk\release\*.apk

echo.
echo To install on your phone:
echo 1. Enable "USB Debugging" in Developer Options
echo 2. Connect phone via USB
echo 3. Run: adb install -r app\build\outputs\apk\release\app-release.apk
echo.
echo OR copy the APK to your phone and install manually.
echo.

pause