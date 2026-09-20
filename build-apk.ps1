<# 
.SYNOPSIS
    Builds Zoro App Store Release APK

.DESCRIPTION
    Cleans and builds the Android app in release mode, outputs APK location
#>

param(
    [switch]$Install,
    [switch]$Clean
)

$projectDir = Join-Path $PSScriptRoot "app-store-client"
$apkPath = Join-Path $projectDir "app\build\outputs\apk\release\app-release.apk"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Building Zoro App Store - Release APK" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $projectDir

if ($Clean) {
    Write-Host "Cleaning previous builds..." -ForegroundColor Yellow
    .\gradlew.bat clean
}

Write-Host "Building Release APK..." -ForegroundColor Green
Write-Host "This may take a few minutes on first run..." -ForegroundColor Gray
Write-Host ""

$startTime = Get-Date
.\gradlew.bat assembleRelease
$endTime = Get-Date

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Red
    Write-Host "  BUILD FAILED!" -ForegroundColor Red
    Write-Host "==========================================" -ForegroundColor Red
    exit 1
}

$duration = $endTime - $startTime
Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host "Time elapsed: $([math]::Round($duration.TotalMinutes, 1)) minutes" -ForegroundColor Gray
Write-Host ""

if (Test-Path $apkPath) {
    $size = (Get-Item $apkPath).Length / 1MB
    Write-Host "APK Location: $apkPath" -ForegroundColor Cyan
    Write-Host "APK Size: $([math]::Round($size, 2)) MB" -ForegroundColor Gray
    
    if ($Install) {
        Write-Host ""
        Write-Host "Installing on connected device..." -ForegroundColor Yellow
        adb install -r $apkPath
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Installation successful!" -ForegroundColor Green
        } else {
            Write-Host "Installation failed. Make sure USB debugging is enabled and device is connected." -ForegroundColor Red
        }
    } else {
        Write-Host ""
        Write-Host "To install on your phone:" -ForegroundColor Yellow
        Write-Host "  1. Enable 'USB Debugging' in Developer Options" -ForegroundColor Gray
        Write-Host "  2. Connect phone via USB" -ForegroundColor Gray
        Write-Host "  3. Run: .\build-apk.ps1 -Install" -ForegroundColor Cyan
        Write-Host "  OR copy APK to phone and install manually" -ForegroundColor Gray
    }
}

Write-Host ""