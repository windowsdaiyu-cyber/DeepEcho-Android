# DeepEcho Mobile v1.12.8 — Test Notes

## Baseline

v1.12.8 was rebuilt from the known-good v1.12.7 one-click release baseline rather than from the earlier failed v1.12.8 attempt.

## Build-stack continuity

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Compose plugin 2.0.21
- Gradle 8.9
- Java 17
- compileSdk 35 / targetSdk 34 / minSdk 26

## Compile fix history

The first v1.12.8 attempt contained invalid explicit `androidx.compose.foundation.layout.weight` imports in two new Compose files. The golden-baseline v1.12.8 source removed those imports and follows the same scope-based `Modifier.weight(...)` pattern as the successful v1.12.7 project.

## Release safeguards

The one-click release runner repeats unit tests and a signed `assembleRelease` before it can create/push tag `v1.12.8`. It also verifies the APK package, version name, version code and permanent signing certificate.
