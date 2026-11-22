plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val repo = (findProperty("repo") as String?) ?: "OWNER/REPO"
val ver = (findProperty("ver") as String?) ?: "1.0.0"
val code = ((findProperty("code") as String?) ?: "1").toInt()

android {
    namespace = "com.nimeko.stream"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nimeko.stream"
        minSdk = 26
        targetSdk = 34
        versionCode = code
        versionName = ver
        buildConfigField("String", "REPO", "\"$repo\"")
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
