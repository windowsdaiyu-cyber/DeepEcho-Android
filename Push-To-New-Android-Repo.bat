@echo off
setlocal
cd /d "%~dp0"

echo DeepEcho Android - separate repository publisher
echo.
echo IMPORTANT: Use ONLY a new/separate Android GitHub repository here.
echo Do NOT enter the Windows DeepEcho repository URL.
echo.
set /p REPOURL=Paste the NEW Android GitHub repository URL (https://github.com/USER/DeepEcho-Android.git): 
if "%REPOURL%"=="" exit /b 1

git init || exit /b 1
git branch -M main || exit /b 1
git add . || exit /b 1
git commit -m "DeepEcho Android v0.1.0 alpha with automatic APK build" || exit /b 1
git remote remove origin >nul 2>nul
git remote add origin "%REPOURL%" || exit /b 1
git push -u origin main || exit /b 1

echo.
echo Uploaded. Open the repository Actions tab and run: DeepEcho Android APK
pause
