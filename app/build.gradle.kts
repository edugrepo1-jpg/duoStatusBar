import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

/**
 * Local secrets from the git-ignored `.env`, or the environment (CI). The Telegram bot token is baked
 * into the APK so the "Collect full log" button can upload the file to the developer's chat in one tap;
 * it is a dedicated bot token that can be revoked, never a personal one.
 */
val localEnv = Properties().apply {
    val file = rootProject.file(".env")
    if (file.exists()) {
        file.readLines()
            .filter { it.contains("=") && !it.trimStart().startsWith("#") }
            .forEach { line ->
                val at = line.indexOf('=')
                setProperty(line.substring(0, at).trim(), line.substring(at + 1).trim())
            }
    }
}

fun secret(name: String): String = System.getenv(name) ?: localEnv.getProperty(name) ?: ""

android {
    namespace = "io.github.kvmy666.duostatusbar"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kvmy666.duostatusbar"
        // Android 14 (API 34) is the floor: the hook targets AOSP/ColorOS/One UI SystemUI views that
        // exist on 14, and nothing in the module needs an API-35 call. targetSdk stays 36.
        minSdk = 34
        targetSdk = 36
        versionCode = 13
        versionName = "1.3.1"
        // Telegram log delivery. Prefer the relay URL (Worker holds the bot token, so no secret is in the
        // APK); otherwise fall back to the direct bot token; otherwise the button opens the share sheet.
        buildConfigField("String", "TELEGRAM_RELAY_URL", "\"${secret("TELEGRAM_RELAY_URL")}\"")
        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"${secret("TELEGRAM_BOT_TOKEN")}\"")
        buildConfigField("String", "TELEGRAM_CHAT_ID", "\"${secret("TELEGRAM_CHAT_ID")}\"")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile", "../keystore.jks"))
                storePassword = keystoreProperties.getProperty("storePassword", "")
                keyAlias = keystoreProperties.getProperty("keyAlias", "")
                keyPassword = keystoreProperties.getProperty("keyPassword", "")
            } else {
                storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    /**
     * Rive ships a native library (librive.so). We keep it EXTRACTED on disk
     * (useLegacyPackaging) so that the module can hand a real file path to the
     * SystemUI process when it loads Rive inside SysUI (probe P-03).
     */
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Issue #4: the Shizuku shell UserService is an AIDL interface (see settings/IShellService.aidl).
        aidl = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    compileOnly(libs.xposed.api)

    implementation(libs.rive.android)
    // Issue #4: optional Shizuku support, used only to hide the stock status-bar icons via the secure
    // `icon_blacklist` setting when the LSPosed view-hiding is not enough. The module itself never
    // depends on Shizuku, so a device without it behaves exactly as before.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // Update checks: a background worker polls GitHub releases and notifies, so a user does not have to
    // hunt for new versions. No server of our own, so it is a periodic poll.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
