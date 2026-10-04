@echo off
setlocal
cd /d "%~dp0"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "OUT=%~dp0deepecho-crash-log.txt"

if not exist "%ADB%" (
  echo [ERROR] adb.exe not found at:
  echo %ADB%
  pause
  exit /b 1
)

echo Connected devices:
"%ADB%" devices
echo.
echo Clearing old log...
"%ADB%" logcat -c

echo Launching DEEP-ECHO...
"%ADB%" shell am force-stop com.deepecho.mobile >nul 2>&1
"%ADB%" shell monkey -p com.deepecho.mobile -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo Waiting 10 seconds for a startup crash...
timeout /t 10 /nobreak >nul

"%ADB%" logcat -d -v time AndroidRuntime:E *:S > "%OUT%"

echo.
echo Crash log saved:
echo %OUT%
start "" notepad.exe "%OUT%"
pause
