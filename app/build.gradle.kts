import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("io.gitlab.arturbosch.detekt")
    // Gradle Play Publisher for automated Play uploads
    id("com.github.triplet.play") version "3.9.1"
}

// Load release signing properties (if present)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    }
}

android {
    namespace = "com.mmgb.snake"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mmgb.snake"
        minSdk = 23
        targetSdk = 36
        versionCode = 7 // production release versie7
        versionName = "7.1" // serie1 interpreted as minor .1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                val storePath = keystoreProps.getProperty("storeFile")
                if (!storePath.isNullOrBlank()) {
                    storeFile = rootProject.file(storePath)
                }
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                val storePath = keystoreProps.getProperty("storeFile")
                val storeFileRef = if (!storePath.isNullOrBlank()) rootProject.file(storePath) else null
                if (storeFileRef != null && storeFileRef.exists()) {
                    signingConfig = signingConfigs.getByName("release")
                } else {
                    // Keystore not present in workspace — skip assigning release signingConfig so assembleRelease can proceed (unsigned)
                }
            }
        }
    }
    // Ensure Java compilation uses 17 (matches Kotlin jvmTarget)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Configure Kotlin JVM toolchain explicitly
kotlin {
    jvmToolchain(17)
}

// Ensure `check` runs detekt
tasks.named("check") {
    dependsOn("detekt")
}

// Configure detekt tasks to use repo config and not fail the build while we triage findings
// Use fully qualified class reference to avoid requiring an import in the middle of the script
tasks.withType(io.gitlab.arturbosch.detekt.Detekt::class.java).configureEach {
    // point to root config
    config.setFrom(rootProject.file("detekt.yml"))
    // don't fail the build on issues right away (CI will report)
    ignoreFailures = true
    buildUponDefaultConfig = true
}

dependencies {
    // Use platform BOM from version catalog
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Add other Material3 artifacts
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptiveNavigationSuite)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.ui.graphics) // for nativeCanvas
    // Add Material Components (provides XML Theme.Material3.* styles)
    implementation(libs.google.material)

    // Add ViewModel Compose integration (version via catalog)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Use lifecycle-runtime-compose for LocalLifecycleOwner without deprecation
    implementation(libs.androidx.lifecycle.runtime.compose)

    // DataStore Preferences for settings & high score persistence (via catalog)
    implementation(libs.androidx.datastore.preferences)

    // Play Integrity API (from version catalog)
    implementation(libs.google.play.integrity)

    // Google Play Games Services SDK (v2) pinned version (avoid using + in production)
    implementation(libs.google.play.games.v2)
    // Google Mobile Ads SDK
    implementation(libs.google.ads)

    // Play Billing
    implementation(libs.google.play.billing)

    // Coroutines (core + Android dispatcher)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit test dependency (JUnit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)

    // Compose UI testing - use BOM aligned versions
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // AndroidX test libs
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.google.ump)

    implementation(libs.kotlinx.serialization.json)
}

// Configure Play Publisher without credentials (passed via -P or env in CI)
play {
    // Use app bundles by default
    defaultToAppBundles.set(true)
    // Track and status can be overridden at runtime via -Pplay.track and -Pplay.releaseStatus
    // Example defaults (safe):
    track.set("internal")
    releaseStatus.set("draft")
}
