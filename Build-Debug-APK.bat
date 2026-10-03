@echo off
setlocal
cd /d "%~dp0"
echo ================================================
echo   DEEP-ECHO Android v0.1.0 Alpha - Debug Build
echo ================================================
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
  echo.
  echo Build failed. Open this folder in Android Studio and install Android SDK 37 / Build Tools 36 if prompted.
  pause
  exit /b 1
)
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
