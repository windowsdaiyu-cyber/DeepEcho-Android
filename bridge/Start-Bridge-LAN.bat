@echo off
setlocal
cd /d "%~dp0"
if not "%~1"=="" set "DEEPECHO_YTDLP=%~1"
set "DEEPECHO_BRIDGE_HOST=0.0.0.0"
echo Starting DeepEcho Bridge for real-phone LAN testing on port 17832...
echo Use your PC's local IPv4 address in DeepEcho Android Settings.
node server.js
pause
