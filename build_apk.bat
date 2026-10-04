@echo off
if /I "%~1"=="__RUN__" goto RUN

rem Keep the terminal open even if something fails before the normal pause.
cmd.exe /k ""%~f0" __RUN__"
exit /b

:RUN
setlocal EnableExtensions EnableDelayedExpansion
title DEEP-ECHO Mobile v1.9.0 - AUTO-UPDATE BUILDER
cd /d "%~dp0"

echo ==============================================
echo   DEEP-ECHO Mobile v1.9.0 - AUTO-UPDATE BUILDER
echo   Java 17 locked - live Gradle output
echo ==============================================
echo.

set "BUILD_LOG=%~dp0deepecho-build.log"
set "JAVA17="
set "GRADLE_BAT="
set "APK=app\build\outputs\apk\debug\app-debug.apk"
set "OUT_APK=%~dp0DEEP-ECHO-Mobile-v1.9.0-AUTO-UPDATE.apk"

echo [0/6] Builder started successfully.
echo Working folder:
echo %CD%
echo.

rem ------------------------------------------------------------
rem 1. Java 17
rem ------------------------------------------------------------
echo [1/6] Finding Java 17...

if exist "%USERPROFILE%\DeepEcho-Android-Local\.local-tools\jdk17\bin\java.exe" (
    set "JAVA17=%USERPROFILE%\DeepEcho-Android-Local\.local-tools\jdk17"
)

if not defined JAVA17 if exist "%~dp0.local-tools\jdk17\bin\java.exe" (
    set "JAVA17=%~dp0.local-tools\jdk17"
)

if not defined JAVA17 (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
        if exist "%%~fD\bin\java.exe" set "JAVA17=%%~fD"
    )
)

if not defined JAVA17 (
    for /d %%D in ("C:\Program Files\Java\jdk-17*") do (
        if exist "%%~fD\bin\java.exe" set "JAVA17=%%~fD"
    )
)

if not defined JAVA17 (
    echo.
    echo [ERROR] Java 17 nahi mila.
    echo Expected:
    echo %USERPROFILE%\DeepEcho-Android-Local\.local-tools\jdk17
    goto FAIL_NO_BUILD
)

set "JAVA_HOME=%JAVA17%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 (
    echo [ERROR] Java 17 launch nahi hua.
    goto FAIL_NO_BUILD
)

echo [OK] Java ready: %JAVA_HOME%
echo.

rem ------------------------------------------------------------
rem 2. Android SDK
rem ------------------------------------------------------------
echo [2/6] Checking Android SDK...

if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

if not exist "%ANDROID_HOME%\platforms\android-35\android.jar" (
    echo.
    echo [ERROR] Android SDK Platform 35 missing:
    echo %ANDROID_HOME%\platforms\android-35
    goto FAIL_NO_BUILD
)

set "SDKP=%ANDROID_HOME:\=\\%"
>local.properties echo sdk.dir=%SDKP%

echo [OK] Android SDK ready.
echo.

rem ------------------------------------------------------------
rem 3. Permanent signing key (critical for Android updates)
rem ------------------------------------------------------------
echo [3/6] Checking permanent signing key...

set "SIGNING_HOME=%USERPROFILE%\DeepEcho-Android-Local"
set "STABLE_KEY=%SIGNING_HOME%\deepecho.keystore"
set "OLD_KEY="

if not exist "%SIGNING_HOME%" mkdir "%SIGNING_HOME%" >nul 2>&1

rem If this project already has a key, preserve it permanently first.
if not exist "%STABLE_KEY%" if exist "deepecho.keystore" (
    echo Preserving current project signing key...
    copy /Y "deepecho.keystore" "%STABLE_KEY%" >nul
)

rem v1.8.0 was the last pre-updater build. Reuse its key automatically when possible
rem so v1.9.0 can install directly over the currently installed app.
if not exist "%STABLE_KEY%" (
    echo Looking for the v1.8.0 signing key in Downloads...
    for /f "usebackq delims=" %%K in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$root=Join-Path $env:USERPROFILE 'Downloads'; if(Test-Path $root){Get-ChildItem -Path $root -Filter 'deepecho.keystore' -File -Recurse -ErrorAction SilentlyContinue ^| Where-Object {$_.FullName -match 'v1\.8\.0'} ^| Sort-Object LastWriteTime -Descending ^| Select-Object -First 1 -ExpandProperty FullName}"`) do set "OLD_KEY=%%K"
    if defined OLD_KEY (
        echo Found previous key:
        echo !OLD_KEY!
        copy /Y "!OLD_KEY!" "%STABLE_KEY%" >nul
    )
)

rem New install only: create one permanent key and keep using it forever.
if not exist "%STABLE_KEY%" (
    echo No previous key found. Creating ONE permanent DeepEcho signing key...
    "%JAVA_HOME%\bin\keytool.exe" -genkeypair ^
        -keystore "%STABLE_KEY%" ^
        -alias deepecho ^
        -keyalg RSA ^
        -keysize 2048 ^
        -validity 36500 ^
        -storepass deepecho ^
        -keypass deepecho ^
        -dname "CN=DEEP-ECHO"
    if errorlevel 1 (
        echo [ERROR] Permanent keystore creation failed.
        goto FAIL_NO_BUILD
    )
)

copy /Y "%STABLE_KEY%" "deepecho.keystore" >nul
if errorlevel 1 (
    echo [ERROR] Could not copy permanent signing key into this project.
    goto FAIL_NO_BUILD
)

if not exist "deepecho.keystore" (
    echo [ERROR] Project signing key is missing.
    goto FAIL_NO_BUILD
)

echo [OK] Permanent signing key ready.
echo Stable key: %STABLE_KEY%
echo IMPORTANT: Is file ko delete mat karna. Future APK updates isi key se sign honge.
echo.

rem ------------------------------------------------------------
rem 4. Gradle
rem ------------------------------------------------------------
echo [4/6] Finding Gradle 8.9...

if exist "%USERPROFILE%\DeepEcho-Android-Local\.local-tools\gradle-8.9\bin\gradle.bat" (
    set "GRADLE_BAT=%USERPROFILE%\DeepEcho-Android-Local\.local-tools\gradle-8.9\bin\gradle.bat"
    echo [OK] Using cached Gradle 8.9.
) else (
    echo Cached Gradle not found. Preparing wrapper fallback...

    if not exist "gradle\wrapper" mkdir "gradle\wrapper" >nul 2>&1

    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        echo Downloading Gradle wrapper jar...

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
            goto FAIL_NO_BUILD
        )
    )

    >"_deepecho_gradle.bat" echo @echo off
    >>"_deepecho_gradle.bat" echo "%%JAVA_HOME%%\bin\java.exe" -classpath "%%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %%*
    set "GRADLE_BAT=%~dp0_deepecho_gradle.bat"
    echo [OK] Wrapper fallback ready.
)

if not exist "%GRADLE_BAT%" (
    echo [ERROR] Gradle launcher missing:
    echo %GRADLE_BAT%
    goto FAIL_NO_BUILD
)

echo.

rem ------------------------------------------------------------
rem 5. Build
rem ------------------------------------------------------------
echo [5/6] Building APK...
echo.
echo IMPORTANT:
echo - Output ab isi terminal me LIVE dikhega.
echo - Window build ke baad khud band nahi hogi.
echo - Clean build nahi, incremental cache use hoga.
echo.

if exist "%APK%" del /q "%APK%" >nul 2>&1
if exist "%OUT_APK%" del /q "%OUT_APK%" >nul 2>&1
if exist "%BUILD_LOG%" del /q "%BUILD_LOG%" >nul 2>&1

call "%GRADLE_BAT%" --build-cache --parallel --console=plain :app:assembleDebug
set "RC=!ERRORLEVEL!"

if not "!RC!"=="0" (
    echo.
    echo ==============================================
    echo   BUILD FAILED
    echo ==============================================
    echo.
    echo Exact error log ab generate ho raha hai...
    echo This second Gradle pass is only for diagnostics and should be faster.
    echo.

    call "%GRADLE_BAT%" --build-cache --parallel --console=plain :app:assembleDebug --stacktrace > "%BUILD_LOG%" 2>&1

    echo.
    echo -------- USEFUL ERRORS --------
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
      "$p='%BUILD_LOG%'; if(Test-Path -LiteralPath $p){$l=Get-Content -LiteralPath $p; $h=$l | Select-String -Pattern '(^e: )|(^ERROR:)|(^FAILURE:)|What went wrong|Execution failed|Could not resolve|Could not find|Unresolved reference|Caused by:|error:' -CaseSensitive:$false; if($h){$h | Select-Object -Last 100 | ForEach-Object {$_.Line}} else {$l | Select-Object -Last 180}}"

    echo.
    echo Full log:
    echo %BUILD_LOG%
    goto FAIL
)

rem ------------------------------------------------------------
rem 6. APK output
rem ------------------------------------------------------------
echo.
echo [6/6] Checking APK...

if not exist "%APK%" (
    echo [ERROR] Gradle reported success but APK is missing:
    echo %CD%\%APK%
    goto FAIL
)

copy /Y "%APK%" "%OUT_APK%" >nul
if errorlevel 1 (
    echo [ERROR] APK copy failed.
    goto FAIL
)

if not exist "%OUT_APK%" (
    echo [ERROR] APK copy verification failed.
    goto FAIL
)

for %%A in ("%OUT_APK%") do set "APK_SIZE=%%~zA"

echo.
echo ==============================================
echo   SUCCESS - APK READY
echo ==============================================
echo.
echo APK:
echo %OUT_APK%
echo.
echo Size: !APK_SIZE! bytes
echo.
echo Folder ab open ho raha hai...
start "" explorer.exe "%~dp0"
echo.
echo Build complete. Is terminal ko jab chaaho close kar sakte ho.
goto DONE

:FAIL_NO_BUILD
echo.
echo ==============================================
echo   BUILDER STOPPED BEFORE GRADLE
echo ==============================================
echo Required tool/path missing hai. Upar exact reason diya hai.
goto DONE

:FAIL
echo.
echo ==============================================
echo   BUILD FAILED
echo ==============================================
echo Error upar hai. deepecho-build.log bhi folder me save hai.
goto DONE

:DONE
echo.
echo ------------------------------------------------
echo Terminal intentionally open rahega.
echo Type EXIT ya window close karo jab finish ho.
echo ------------------------------------------------
exit /b
