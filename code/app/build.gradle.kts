plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// versionCode 取当前提交所在分支的 commit 数，取不到时回退为 1
fun gitCommitCount(): Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "io.github.imroxy2222.coloros17perfecticon"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.imroxy2222.ColorOS17PerfectIcon"
        minSdk = 34
        targetSdk = 36
        versionCode = gitCommitCount()
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
