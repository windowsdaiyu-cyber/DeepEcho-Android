@echo off
setlocal
cd /d "%~dp0"
node bridge\test.js || exit /b 1
call gradlew.bat :app:lintDebug :app:testDebugUnitTest
