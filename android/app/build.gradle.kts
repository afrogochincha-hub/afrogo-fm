plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val afrogoApiBaseUrl = providers.gradleProperty("afrogoApiBaseUrl").orElse(providers.environmentVariable("AFROGO_API_BASE_URL")).orElse("https://afrogochincha.duckdns.org/afrogo-api").get().trim().replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.afrogo.fm"
    buildFeatures { buildConfig = true }
    compileSdk = 35
    defaultConfig {
        applicationId = "com.afrogo.fm"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
        buildConfigField("String", "AFROGO_API_BASE_URL", "\"$afrogoApiBaseUrl\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
