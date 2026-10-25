import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Production API; release builds always use it. */
val productionApiUrl = "https://api.wizjer.org"

/**
 * Debug builds can point at a development server through `wizjer.apiUrl` in the
 * (git-ignored) local.properties, e.g. `wizjer.apiUrl=http://192.168.1.10:4000`.
 */
val debugApiUrl: String = Properties()
    .apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    .getProperty("wizjer.apiUrl") ?: productionApiUrl

android {
    namespace = "org.wizjer.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.wizjer.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Plain HTTP is allowed in debug builds only, for local servers
            // (src/debug/res/xml/network_security_config.xml).
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            // HTTPS only: release builds refuse cleartext traffic.
            buildConfigField("String", "API_BASE_URL", "\"$productionApiUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Lists the bundled languages (en, pl, uk) in the system per-app language settings.
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    // Android's org.json is only a stub in JVM tests; the AI client's tests parse real JSON.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
