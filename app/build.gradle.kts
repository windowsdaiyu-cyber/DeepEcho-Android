plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Agar playback band ho jaye toh sabse pehle ye version update karo ----
// Latest tag yahan milta hai: https://github.com/TeamNewPipe/NewPipeExtractor/releases
val newPipeExtractorVersion = "v0.26.5"

android {
    namespace = "com.deepecho.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deepecho.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "1.9.0"
    }

    // Ek fixed keystore (build_apk.bat bana deta hai) taaki updates purani APK ke upar install ho sakein
    val ks = rootProject.file("deepecho.keystore")
    signingConfigs {
        if (ks.exists()) {
            create("shared") {
                storeFile = ks
                storePassword = "deepecho"
                keyAlias = "deepecho"
                keyPassword = "deepecho"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (ks.exists()) signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (ks.exists()) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
                "/META-INF/{AL2.0,LGPL2.1}"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("com.github.teamnewpipe:NewPipeExtractor:$newPipeExtractorVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
