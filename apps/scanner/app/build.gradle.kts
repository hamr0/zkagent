import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // Kotlin package/namespace left as upstream's (fork of spikes/m0, itself a fork
    // of tananaev/passport-reader) — cosmetic only for a throwaway spike. The
    // applicationId is changed below so this installs alongside/independently of
    // the M0 spike APK, which is what TEST 1's uninstall/reinstall protocol needs.
    namespace = "com.tananaev.passportreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zkagent.scanner"
        // minSdk 30: KeyGenParameterSpec.Builder#setIsStrongBoxBacked (API 28)
        // and per-use auth via setUserAuthenticationParameters(0, ...) (API 30)
        // are both load-bearing (§6.2 item 1/2) — same reasoning as
        // spikes/m2-session-poc, which this app's key/session code is drawn
        // from. Single real target device for M2 (Pixel 6a, Android 14+).
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    // NEW — AGP 8 requires explicit opt-in for the generated BuildConfig
    // class. Needed for BuildConfig.DEBUG, which gates DeviceKey's dev
    // attester-public-key export (long-press KEY TEST) to debug builds only —
    // absent from release, no other use.
    buildFeatures {
        buildConfig = true
    }

    // Canonical.kt and MasterlistVerifier.kt's CMS logic have no Android
    // framework dependency EXCEPT android.util.Log calls for diagnostics —
    // returnDefaultValues lets those no-op in a plain JVM unit test instead
    // of throwing, without needing Robolectric for logic this simple.
    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(libs.jnbis) // ImageUtil.kt (unused DG2/fingerprint decode path, kept for compile compat)
    implementation(libs.bcpkix.jdk15on) // do not update — also supplies CMS (org.bouncycastle.cms.*) for §6.2 item 7
    implementation(libs.commons.io)
    implementation(libs.androidx.biometric) // §6.2 item 2 — BiometricPrompt (biometric-or-device-credential gate)
    // §6.2 item 8 QR fallback, live camera scan (finding #18 fix, D68 part
    // b, 2026-09-03) — Google's Code Scanner API. Evaluated against
    // com.google.mlkit:barcode-scanning (bundled model) and chosen instead
    // because its scan UI runs inside a Play-services-owned delegate
    // activity in a separate process: it requests android.permission.CAMERA
    // itself, at that process's own manifest, so THIS app's manifest gains
    // no new <uses-permission> (confirmed by inspecting the aar's own
    // AndroidManifest.xml — it declares zero permissions) and no new
    // network dependency of this app's own, meeting item 10's constraint.
    // ML Kit's bundled model would instead require this app to declare
    // CAMERA and build its own CameraX preview.
    implementation(libs.play.services.code.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.json) // see libs.versions.toml — real org.json for unit tests only
    // bcpkix-jdk15on (CMS) and bouncycastle asn1 classes are pure-Java, so
    // MasterlistVerifier's CMS logic is exercisable in a plain JVM unit test
    // without any Android framework classes — Canonical.kt likewise has zero
    // Android dependencies.
}
