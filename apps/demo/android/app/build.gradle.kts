import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "test.zkagent.m2handoff"
    compileSdk = 36

    defaultConfig {
        applicationId = "test.zkagent.m2handoff"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-spike"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Zero third-party deps: java.security for ES256, org.json (in the Android
    // platform) for JSON, java.net for HTTP. No PII, no chip reading — handoff only.
}
