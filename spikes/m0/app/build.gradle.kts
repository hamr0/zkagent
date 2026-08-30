import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// M1 integrity POC: cloud project number stays out of source, read from a
// gitignored local.properties key at build time instead.
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
val m1IntegrityCloudProjectNumber: String =
    localProperties.getProperty("m1.integrity.cloudProjectNumber", "")

android {
    namespace = "com.tananaev.passportreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tananaev.passportreader"
        minSdk = 23
        targetSdk = 36
        versionCode = 23
        versionName = "3.4"

        buildConfigField(
            "String",
            "M1_INTEGRITY_CLOUD_PROJECT_NUMBER",
            "\"$m1IntegrityCloudProjectNumber\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "default"
    productFlavors {
        create("regular") {
            isDefault = true
            extra["enableCrashlytics"] = false
        }
    }

    buildTypes {
        getByName("release") {
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf("META-INF/LICENSE", "META-INF/NOTICE")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.materialdatetimepicker)
    implementation(libs.jmrtd)
    implementation(libs.scuba.sc.android)
    implementation(libs.spongycastle.prov)
    implementation(libs.jnbis)
    implementation(libs.bcpkix.jdk15on) // do not update
    implementation(libs.commons.io)
    implementation("com.google.android.play:integrity:1.6.0")
}
