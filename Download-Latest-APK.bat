@echo off
title PulseWave Release Downloader
echo ===================================================
echo   Downloading Latest PulseWave APK to Desktop...
echo ===================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\download-desktop-release.ps1"
echo.
pause
