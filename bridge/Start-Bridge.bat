@echo off
setlocal
cd /d "%~dp0"
if not "%~1"=="" set "DEEPECHO_YTDLP=%~1"
echo Starting DeepEcho Android Alpha Bridge...
echo Emulator will use http://10.0.2.2:17832
node server.js
pause
