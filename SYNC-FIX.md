# Android Studio Sync Fix

This package removes the obsolete `org.jetbrains.kotlin.android` plugin from the AGP 9.4 project and removes the legacy `android.kotlinOptions` block so AGP built-in Kotlin is used. The Gradle wrapper timeout is also raised to 120 seconds for slower networks.
