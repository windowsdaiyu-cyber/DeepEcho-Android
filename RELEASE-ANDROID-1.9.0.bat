@echo off
setlocal EnableExtensions EnableDelayedExpansion
title DEEP-ECHO Mobile 1.9.0 - FINAL RELEASE
cd /d "%~dp0"

set "VERSION=1.9.0"
set "TAG=v1.9.0"
set "OWNER=windowsdaiyu-cyber"
set "REPO=DeepEcho-Android"
set "FULL_REPO=%OWNER%/%REPO%"
set "REMOTE=https://github.com/%FULL_REPO%.git"
set "REPO_WEB=https://github.com/%FULL_REPO%"
set "ROOT=%~dp0"
set "LOCAL_HOME=%USERPROFILE%\DeepEcho-Android-Local"
set "STABLE_KEY=%LOCAL_HOME%\deepecho.keystore"
set "WORK=%LOCAL_HOME%\release-work-%VERSION%"
set "CLONE=%WORK%\repo"
set "BUILD_LOG=%ROOT%release-build.log"
set "RELEASE_DIR=%ROOT%release-input"
set "RELEASE_APK=%RELEASE_DIR%\DEEP-ECHO-Mobile-%VERSION%.apk"
set "RELEASE_SHA=%RELEASE_APK%.sha256"
set "GRADLE_BAT="
set "JAVA17="
set "BUILD_TOOLS="

echo ============================================================
echo   DEEP-ECHO MOBILE %VERSION% - FINAL RELEASE
echo ============================================================
echo.
echo Android repo ONLY:
echo   %REPO_WEB%
echo.
echo No force-push.
echo Existing tag %TAG% mile toh release STOP hogi.
echo PC/Windows DEEP-ECHO repo touch nahi hoga.
echo ============================================================
echo.

rem ============================================================
rem [1/9] Verify exact tested source
rem ============================================================
echo [1/9] Verifying tested v%VERSION% source...

if not exist "app\build.gradle.kts" (
    echo [ERROR] app\build.gradle.kts missing.
    goto FAIL
)
if not exist ".github\workflows\release-android.yml" (
    echo [ERROR] release workflow missing.
    goto FAIL
)
if not exist "PROTECTED-SOURCE-SHA256-1.9.0.txt" (
    echo [ERROR] protected-source manifest missing.
    goto FAIL
)

findstr /I /C:"applicationId = \"com.deepecho.mobile\"" "app\build.gradle.kts" >nul
if errorlevel 1 (
    echo [ERROR] Wrong Android package. Release blocked.
    goto FAIL
)

findstr /I /C:"versionName = \"%VERSION%\"" "app\build.gradle.kts" >nul
if errorlevel 1 (
    echo [ERROR] App version is not %VERSION%.
    goto FAIL
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%release-tools\verify_release_source.ps1"
if errorlevel 1 goto FAIL

echo [OK] Exact tested app source verified.
echo.

rem ============================================================
rem [2/9] Verify Git + remote tag is unused
rem ============================================================
echo [2/9] Checking Git and release tag...

where git.exe >nul 2>&1
if errorlevel 1 (
    if exist "%ProgramFiles%\Git\cmd\git.exe" (
        set "PATH=%ProgramFiles%\Git\cmd;%PATH%"
    ) else (
        echo [ERROR] Git for Windows missing.
        goto FAIL
    )
)

set "TAG_FILE=%TEMP%\deepecho-android-tag-%RANDOM%.txt"
git ls-remote --tags "%REMOTE%" "refs/tags/%TAG%" > "%TAG_FILE%" 2>nul
set "TAG_RC=!ERRORLEVEL!"
if not "!TAG_RC!"=="0" (
    del /q "%TAG_FILE%" >nul 2>&1
    echo [ERROR] Android GitHub repo could not be reached.
    goto FAIL
)
set "REMOTE_TAG="
for /f "usebackq delims=" %%T in ("%TAG_FILE%") do set "REMOTE_TAG=%%T"
del /q "%TAG_FILE%" >nul 2>&1

if defined REMOTE_TAG (
    echo [ERROR] Tag %TAG% already exists on GitHub.
    echo Version reuse/overwrite blocked.
    goto FAIL
)
echo [OK] Tag %TAG% is free.
echo.

rem ============================================================
rem [3/9] Java 17 + Android SDK + permanent signing key
rem ============================================================
echo [3/9] Checking release build environment...

if exist "%LOCAL_HOME%\.local-tools\jdk17\bin\java.exe" set "JAVA17=%LOCAL_HOME%\.local-tools\jdk17"
if not defined JAVA17 if exist "%ROOT%.local-tools\jdk17\bin\java.exe" set "JAVA17=%ROOT%.local-tools\jdk17"
if not defined JAVA17 (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA17=%%~fD"
)
if not defined JAVA17 (
    echo [ERROR] Java 17 missing.
    goto FAIL
)

set "JAVA_HOME=%JAVA17%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

if not exist "%ANDROID_HOME%\platforms\android-35\android.jar" (
    echo [ERROR] Android SDK Platform 35 missing.
    goto FAIL
)

if not exist "%STABLE_KEY%" (
    echo [ERROR] Permanent Android signing key missing:
    echo   %STABLE_KEY%
    echo.
    echo Release ke waqt NEW key create nahi ki jayegi.
    echo Old installed v1.9.0 ke update chain ke liye tested key hi required hai.
    goto FAIL
)

copy /Y "%STABLE_KEY%" "%ROOT%deepecho.keystore" >nul
if errorlevel 1 (
    echo [ERROR] Permanent key project me stage nahi hui.
    goto FAIL
)

set "SDKP=%ANDROID_HOME:\=\\%"
>local.properties echo sdk.dir=%SDKP%

echo [OK] Java 17 + SDK 35 + permanent signing key ready.
echo.

rem ============================================================
rem [4/9] Run release regression build/tests
rem ============================================================
echo [4/9] Running Gradle tests + signed release build...

if exist "%LOCAL_HOME%\.local-tools\gradle-8.9\bin\gradle.bat" (
    set "GRADLE_BAT=%LOCAL_HOME%\.local-tools\gradle-8.9\bin\gradle.bat"
) else (
    if not exist "gradle\wrapper" mkdir "gradle\wrapper" >nul 2>&1

    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        echo Preparing Gradle 8.9 wrapper...
        where curl.exe >nul 2>&1
        if not errorlevel 1 (
            curl.exe -L -f --retry 3 --connect-timeout 20 ^
              "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" ^
              -o "gradle\wrapper\gradle-wrapper.jar"
        ) else (
            powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
              "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
        )
        if not exist "gradle\wrapper\gradle-wrapper.jar" (
            echo [ERROR] Gradle wrapper jar download failed.
            goto FAIL
        )
    )

    >"_deepecho_gradle.bat" echo @echo off
    >>"_deepecho_gradle.bat" echo "%%JAVA_HOME%%\bin\java.exe" -classpath "%%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %%*
    set "GRADLE_BAT=%ROOT%_deepecho_gradle.bat"
)

if exist "%BUILD_LOG%" del /q "%BUILD_LOG%" >nul 2>&1

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%release-tools\release_gradle.ps1" ^
  -GradleBat "%GRADLE_BAT%" -LogPath "%BUILD_LOG%"
if errorlevel 1 (
    echo.
    echo [ERROR] Gradle tests/release build failed.
    echo Full log:
    echo   %BUILD_LOG%
    goto FAIL
)

set "BUILT_APK=%ROOT%app\build\outputs\apk\release\app-release.apk"
if not exist "%BUILT_APK%" (
    echo [ERROR] app-release.apk missing after successful Gradle build.
    goto FAIL
)

echo [OK] Gradle test task + release build passed.
echo.

rem ============================================================
rem [5/9] Verify signed APK/package/version + prepare updater asset
rem ============================================================
echo [5/9] Verifying signed APK and updater asset...

for /f "delims=" %%B in ('dir /b /ad "%ANDROID_HOME%\build-tools" 2^>nul ^| sort /R') do (
    if not defined BUILD_TOOLS if exist "%ANDROID_HOME%\build-tools\%%B\apksigner.bat" set "BUILD_TOOLS=%ANDROID_HOME%\build-tools\%%B"
)

if not defined BUILD_TOOLS (
    echo [ERROR] Android build-tools/apksigner not found.
    goto FAIL
)

call "%BUILD_TOOLS%\apksigner.bat" verify --verbose --print-certs "%BUILT_APK%"
if errorlevel 1 (
    echo [ERROR] Release APK signature verification failed.
    goto FAIL
)

if exist "%BUILD_TOOLS%\aapt.exe" (
    "%BUILD_TOOLS%\aapt.exe" dump badging "%BUILT_APK%" > "%TEMP%\deepecho-badging-%RANDOM%.txt"
    set "BADGING_RC=!ERRORLEVEL!"
    if not "!BADGING_RC!"=="0" (
        echo [ERROR] APK package/version inspection failed.
        goto FAIL
    )
    set "BADGING_FILE="
    for /f "delims=" %%F in ('dir /b /o-d "%TEMP%\deepecho-badging-*.txt" 2^>nul') do if not defined BADGING_FILE set "BADGING_FILE=%TEMP%\%%F"
    findstr /C:"package: name='com.deepecho.mobile'" "!BADGING_FILE!" >nul
    if errorlevel 1 (
        echo [ERROR] APK package is not com.deepecho.mobile.
        del /q "!BADGING_FILE!" >nul 2>&1
        goto FAIL
    )
    findstr /C:"versionName='%VERSION%'" "!BADGING_FILE!" >nul
    if errorlevel 1 (
        echo [ERROR] APK versionName is not %VERSION%.
        del /q "!BADGING_FILE!" >nul 2>&1
        goto FAIL
    )
    del /q "!BADGING_FILE!" >nul 2>&1
)

if exist "%RELEASE_DIR%" rmdir /s /q "%RELEASE_DIR%" >nul 2>&1
mkdir "%RELEASE_DIR%" >nul 2>&1

copy /Y "%BUILT_APK%" "%RELEASE_APK%" >nul
if errorlevel 1 (
    echo [ERROR] Release APK preparation failed.
    goto FAIL
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p='%RELEASE_APK%'; $h=(Get-FileHash -Algorithm SHA256 -LiteralPath $p).Hash.ToLowerInvariant(); $n=[IO.Path]::GetFileName($p); Set-Content -LiteralPath '%RELEASE_SHA%' -Value ($h+'  '+$n) -Encoding ascii"
if errorlevel 1 (
    echo [ERROR] SHA256 generation failed.
    goto FAIL
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%release-tools\verify_release_source.ps1"
if errorlevel 1 goto FAIL

echo [OK] Signed APK + SHA256 ready.
echo   %RELEASE_APK%
echo.

rem ============================================================
rem [6/9] Clone the real Android repo safely
rem ============================================================
echo [6/9] Cloning Android GitHub repo...

if exist "%WORK%" rmdir /s /q "%WORK%" >nul 2>&1
mkdir "%WORK%" >nul 2>&1

git clone "%REMOTE%" "%CLONE%"
if errorlevel 1 (
    echo [ERROR] Android repo clone failed.
    goto FAIL
)

cd /d "%CLONE%"
git remote get-url origin | findstr /I /C:"windowsdaiyu-cyber/DeepEcho-Android" >nul
if errorlevel 1 (
    echo [ERROR] Clone remote safety check failed.
    goto FAIL
)
echo [OK] Correct Android repo cloned.
echo.

rem ============================================================
rem [7/9] Sync exact release source, commit, push main
rem ============================================================
echo [7/9] Syncing exact v%VERSION% release source and pushing main...

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%release-tools\sync_to_clone.ps1"
if errorlevel 1 (
    echo [ERROR] Source sync failed.
    goto FAIL
)

cd /d "%CLONE%"

git config user.name "windowsdaiyu-cyber"
git config user.email "windowsdaiyu-cyber@users.noreply.github.com"

git add -A
if errorlevel 1 goto FAIL

git diff --cached --name-only | findstr /I /R "deepecho\.keystore$" >nul
if not errorlevel 1 (
    echo [ERROR] Private signing key staged. Release blocked.
    goto FAIL
)

git diff --cached --quiet
if errorlevel 1 (
    git commit -m "DEEP-ECHO Mobile %TAG% release"
    if errorlevel 1 goto FAIL
) else (
    echo [INFO] Main already matches this release source.
)

git push origin main
if errorlevel 1 (
    echo [ERROR] main push failed.
    echo No force-push was attempted.
    goto FAIL
)
echo [OK] main pushed.
echo.

rem ============================================================
rem [8/9] Create annotated tag and trigger GitHub Actions
rem ============================================================
echo [8/9] Creating %TAG% and triggering GitHub Actions...

git ls-remote --tags origin "refs/tags/%TAG%" > "%TEMP%\deepecho-tag-final-%RANDOM%.txt" 2>nul
set "TAG_RC=!ERRORLEVEL!"
if not "!TAG_RC!"=="0" (
    echo [ERROR] Could not re-check remote tag.
    goto FAIL
)
set "REMOTE_TAG="
for /f "usebackq delims=" %%T in ("%TEMP%\deepecho-tag-final-*.txt") do set "REMOTE_TAG=%%T"
del /q "%TEMP%\deepecho-tag-final-*.txt" >nul 2>&1
if defined REMOTE_TAG (
    echo [ERROR] Tag appeared before creation. Refusing overwrite.
    goto FAIL
)

git tag -a "%TAG%" -m "DEEP-ECHO Mobile %TAG%"
if errorlevel 1 goto FAIL

git push origin "%TAG%"
if errorlevel 1 goto FAIL

echo [OK] Tag pushed. GitHub Actions release started.
echo.

rem ============================================================
rem [9/9] Wait for published Release + updater assets
rem ============================================================
echo [9/9] Waiting for GitHub Release verification...

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%release-tools\wait_for_release.ps1"
if errorlevel 1 (
    echo.
    echo [ERROR] Tag was pushed, but GitHub Release was not verified.
    echo Check Actions:
    echo   %REPO_WEB%/actions
    goto FAIL_AFTER_TAG
)

echo.
echo ============================================================
echo   [9/9] DONE - RELEASE VERIFIED
echo ============================================================
echo Version : %VERSION%
echo Tag     : %TAG%
echo Repo    : %REPO_WEB%
echo Release : %REPO_WEB%/releases/tag/%TAG%
echo.
echo APK + SHA256 are published.
echo v1.9.0+ installed apps can discover future newer releases.
echo PC/Windows DEEP-ECHO repo was not touched.
echo ============================================================

start "" "%REPO_WEB%/releases/tag/%TAG%" >nul 2>&1
goto SUCCESS

:FAIL_AFTER_TAG
echo.
echo IMPORTANT:
echo %TAG% is already pushed. Do NOT run this BAT again with the same version.
echo Fix the GitHub workflow/release from the Actions page instead.
goto END_FAIL

:FAIL
echo.
echo ============================================================
echo   RELEASE STOPPED SAFELY
echo ============================================================
echo No force-push was used.
echo If tag was not pushed yet, fix the error and run again.
goto END_FAIL

:SUCCESS
del /q "%ROOT%deepecho.keystore" >nul 2>&1
del /q "%ROOT%local.properties" >nul 2>&1
del /q "%ROOT%_deepecho_gradle.bat" >nul 2>&1
echo.
pause
exit /b 0

:END_FAIL
del /q "%ROOT%deepecho.keystore" >nul 2>&1
echo.
pause
exit /b 1
