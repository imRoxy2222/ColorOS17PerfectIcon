plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.imroxy2222.coloros17perfecticon"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.imroxy2222.ColorOS17PerfectIcon"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.12.4")

    implementation("androidx.compose.runtime:runtime:1.10.6")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-graphics:1.10.6")
    implementation("androidx.compose.foundation:foundation:1.10.6")

    implementation("top.yukonga.miuix.kmp:miuix:0.8.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
