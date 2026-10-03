plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9.4 built-in Kotlin defaults to Kotlin/KGP 2.2.10.
    // Keep the Compose compiler plugin on the same Kotlin line.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
