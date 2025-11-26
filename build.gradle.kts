// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Add detekt Gradle plugin (bumped to latest patch)
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
