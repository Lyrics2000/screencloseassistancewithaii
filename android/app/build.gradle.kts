import java.net.URI
import java.net.URLEncoder
import java.util.Properties

plugins {
    
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * ADR (build-time secret guard): configuration comes from local.properties or environment
 * variables (CI). Only NON-secret identifiers are compiled into BuildConfig: the Entra client id,
 * the tenant id, the signature hash and an OAuth-protected flow URL. The build FAILS if:
 *   - the flow URL is not https or contains a SAS signature (sig=), which would be a bearer
 *     secret embedded in the APK (requirement 8);
 *   - any configured value looks like an Anthropic API key (sk-ant-) (requirement 1).
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(key: String, env: String): String =
    (System.getenv(env) ?: localProps.getProperty(key) ?: "").trim()

val entraClientId = cfg("sca.entra.clientId", "SCA_ENTRA_CLIENT_ID").let { if (it.isEmpty()) "00000000-0000-0000-0000-000000000000" else it }
val entraTenantId = cfg("sca.entra.tenantId", "SCA_ENTRA_TENANT_ID").let { if (it.isEmpty()) "00000000-0000-0000-0000-000000000000" else it }
val entraSignatureHash = cfg("sca.entra.signatureHash", "SCA_ENTRA_SIGNATURE_HASH").let { if (it.isEmpty()) "AbCdEfGhIjKlMnOpQrStUvWxYz0=" else it }
val flowEndpointUrl = cfg("sca.flow.endpointUrl", "SCA_FLOW_ENDPOINT_URL").let { if (it.isEmpty()) "https://example.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/00000000000000000000000000000000/triggers/manual/paths/invoke?api-version=1" else it }

val guidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun validateScaConfig() {
    val problems = mutableListOf<String>()
    listOf(entraClientId, entraTenantId, entraSignatureHash, flowEndpointUrl).forEach {
        if (it.contains("sk-ant-", ignoreCase = true)) problems += "An Anthropic API key was found in build configuration. Remove it; the key belongs only in the Power Platform connection."
    }
    if (!guidRegex.matches(entraClientId)) problems += "sca.entra.clientId must be a GUID"
    if (!guidRegex.matches(entraTenantId)) problems += "sca.entra.tenantId must be a GUID"
    if (entraSignatureHash.isEmpty()) problems += "sca.entra.signatureHash is required"
    val uri = runCatching { URI(flowEndpointUrl) }.getOrNull()
    if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) problems += "sca.flow.endpointUrl must be an absolute https URL"
    if (flowEndpointUrl.contains("sig=", ignoreCase = true)) problems += "sca.flow.endpointUrl contains a SAS signature (sig=). Set the trigger to 'Specific users in my tenant' and use the OAuth URL."
    if (problems.isNotEmpty()) {
        throw GradleException("SCA-BUILD-CONFIG: " + problems.joinToString("; "))
    }
}

android {
    namespace = "com.schoolproject.screenclose"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.schoolproject.screenclose"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ENTRA_CLIENT_ID", "\"$entraClientId\"")
        buildConfigField("String", "ENTRA_TENANT_ID", "\"$entraTenantId\"")
        buildConfigField("String", "ENTRA_REDIRECT_URI", "\"msauth://com.schoolproject.screenclose/${URLEncoder.encode(entraSignatureHash, "UTF-8")}\"")
        buildConfigField("String", "FLOW_ENDPOINT_URL", "\"$flowEndpointUrl\"")
        buildConfigField("String", "FLOW_SCOPE", "\"https://service.flow.microsoft.com//.default\"")
        buildConfigField("int", "MAX_IMAGE_LONG_EDGE", "1568")
        buildConfigField("int", "JPEG_QUALITY", "80")
        manifestPlaceholders["msalSignatureHashPath"] = "/$entraSignatureHash"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // For a school project the debug key signs release builds; replace with your own keystore for distribution.
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
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = false
        unitTests.isReturnDefaultValues = false
    }
}

tasks.named("preBuild") {
    doFirst { validateScaConfig() }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.msal)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
