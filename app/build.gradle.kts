plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.surroundsplit"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.surroundsplit"
        minSdk = 23            // AudioTrack.setPreferredDevice needs API 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-common:$media3")

    // OPTIONAL but strongly recommended: software FFmpeg decoders (AC3/EAC3/DTS/TrueHD -> PCM 5.1).
    // Many TVs have no platform decoder for these. Check the NextLib README for the current
    // version that matches your Media3 version, then uncomment:
    // implementation("io.github.anilbeesetti:nextlib-media3ext:<version>")
}
