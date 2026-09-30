plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/*
 * Test-bed target app. It shows a clearly labelled FAKE interstitial with a countdown, a close
 * "X" and an "Install" call-to-action, so CLOSE / WAIT / UNSAFE behaviour can be demonstrated
 * without touching real ad networks. Its package is the default entry in sca_PackageAllowlist.
 */
android {
    namespace = "com.schoolproject.adtestbed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.schoolproject.adtestbed"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
