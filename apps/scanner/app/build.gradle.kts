import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// §6.2 item 24 (D70(c)) — visible version stamp. Computed once at
// configure time (not per-task) via plain ProcessBuilder rather than a
// Gradle exec API, so it has no dependency on which exec surface this
// Gradle version exposes. Falls back to "nogit" when git itself is
// unavailable (e.g. a source tarball with no .git directory) rather than
// failing the build over a cosmetic stamp — this must never be load-bearing
// for anything, only diagnostic.
fun runGitCommand(vararg args: String, workingDir: java.io.File = projectDir): String? = try {
    val process = ProcessBuilder("git", *args)
        .directory(workingDir)
        .redirectErrorStream(false)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode == 0) output else null
} catch (e: Exception) {
    null
}

val gitShortSha: String = run {
    val sha = runGitCommand("rev-parse", "--short=7", "HEAD")
    if (sha.isNullOrBlank()) {
        "nogit"
    } else {
        // Scoped to apps/scanner (the module root, one level up from this
        // app/ subproject), not the whole monorepo — otherwise an unrelated
        // dirty file under e.g. docs/ would false-stamp a clean scanner
        // build as "-dirty". apps/scanner is the right scope because the
        // module's build inputs live there too (root build.gradle.kts,
        // settings.gradle.kts, gradle.properties), not just under app/.
        val porcelain = runGitCommand(
            "status", "--porcelain", "--", ".",
            workingDir = projectDir.parentFile,
        )
        if (!porcelain.isNullOrEmpty()) "$sha-dirty" else sha
    }
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
        versionCode = 3
        // D72 (2026-09-03): lockstep versioning — versionName now tracks the
        // repo's single release version (packages/chiproof/package.json +
        // this field + one CHANGELOG.md section, bumped together every
        // release), not an app-only number.
        versionName = "0.6.0"

        // §6.2 item 24 (D70(c)) — exposed as BuildConfig.GIT_SHA, consumed
        // by VersionStamp.format(). Not a manifest label: a manifest
        // placeholder can't feed both the scan-pane footer and each log
        // entry's technical line from one source the way a BuildConfig
        // constant can.
        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")
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
    // §6.2 item 8 QR fallback (D69, 2026-09-03): NO in-app scanner
    // dependency. The Google Code Scanner API tried under finding #18 /
    // D68(b) was removed the same day it was added — a device test showed
    // it still runs in a Play services process, pulls Google's
    // data-transport telemetry components into the merged manifest, and
    // downloads its module from Google on first use, which the app cannot
    // have zero doubt about. The proven route instead: the verifier renders
    // the QR, the user scans it with any camera app, and that app's own
    // av:// VIEW intent lands directly in RegularActivity — no scanner
    // library, no CAMERA permission, no extra process, ever, in this app.

    testImplementation(libs.junit)
    testImplementation(libs.json) // see libs.versions.toml — real org.json for unit tests only
    // bcpkix-jdk15on (CMS) and bouncycastle asn1 classes are pure-Java, so
    // MasterlistVerifier's CMS logic is exercisable in a plain JVM unit test
    // without any Android framework classes — Canonical.kt likewise has zero
    // Android dependencies.
}
