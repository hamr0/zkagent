import groovy.json.JsonSlurper
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

// §6.7 POC (D82/D84, milestones.md §6.7.5) — operator.json read + validated
// at CONFIGURE time (Gradle Mechanism A, §6.7.2). No file ships inside the
// APK; a bad file fails the build, never a silent default (rule 9). Uses
// Groovy's JsonSlurper (bundled with Gradle, zero new dependency per
// AGENT_RULES's dependency hierarchy) rather than a JSON library dependency.

/** Parsed, validated operator.json — see [loadOperatorConfig]. Lists are
 * threaded through as comma-joined `String` [BuildConfig] fields (not
 * `String[]`): a single scalar type needs no per-entry escaping/array
 * literal syntax, and [OperatorPolicy] on the Kotlin side already has to
 * split/trim/lowercase hostnames regardless of the wire shape, so nothing
 * is lost by picking the simpler `BuildConfig` type. */
data class OperatorConfig(
    val operatorName: String,
    val contactUrl: String,
    val tiers: String,
    val thresholds: List<Int>,
    // Item 23 (D109(i)1): `verifiers` is EITHER a list of exact hostnames OR the open form "any". The open form is a
    // separate boolean (never a list entry), so it can never travel through the comma-joined hostname field.
    val verifiersAny: Boolean,
    val verifiers: List<String>,
    val multiThresholdVerifiers: List<String>,
    val evidencePlug: String,
    // M3b (D109(b), §6.9.2 items 1-2): the tier-C operator switch and the exact-hostname pin list.
    // Default off/empty — see rules 7, 10 and 11 in [loadOperatorConfig].
    val tierCEnabled: Boolean,
    val tierCVerifiers: List<String>,
    val appName: String,
)

// D74's fixed, published threshold list — the ONLY set of legal values an
// operator.json may name (rule 3). Spelled out here independently of
// ThresholdPolicy.kt's own copy (that class reads its PRESETS FROM this
// build's BuildConfig output, so this is the one place that isn't itself
// downstream of operator.json).
val ALLOWED_THRESHOLDS_D74 = setOf(15, 16, 18, 21, 60, 65)

// §6.7.3 — "no attestation plug exists in the repo to name as a second enum
// value yet, so the schema reserves the field but validates against a list
// of exactly one until one ships." When a real plug lands in
// packages/chiproof's registry, this list (and rule 6 below) grows to
// reflect it — there is no registry Gradle can introspect today (chiproof's
// plugs are registered at JS runtime, from config.evidence.plugs, not
// discoverable statically at Android build time).
val ALLOWED_EVIDENCE_PLUGS = setOf("none")

/** Rule 4/5 shape check: a bare hostname — no scheme, no port, no
 * wildcard. */
fun isBareHostname(value: Any?): Boolean {
    if (value !is String || value.isEmpty()) return false
    if (value.contains("://")) return false
    if (value.contains("*")) return false
    if (value.contains(":")) return false
    // D86: `verifiers`/`multi_threshold_verifiers` entries are comma-joined
    // into a single BuildConfig field and comma-split back apart at read
    // time (OperatorPolicy.parseHostnameList) — a literal comma in one
    // entry here would silently expand into two allowlisted hostnames.
    // Quotes/backslashes are already escaped safely by
    // escapeForStringLiteral, but a hostname containing them can never
    // match a real host anyway, so all three are rejected loudly here
    // instead of failing silently or matching nothing.
    if (value.contains(",")) return false
    if (value.contains("\"")) return false
    if (value.contains("\\")) return false
    return true
}

/** Escapes a value for embedding in a Kotlin/BuildConfig `String` literal
 * (backslash and double-quote only — operator-supplied strings are not
 * otherwise constrained). */
fun escapeForStringLiteral(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

/** Reads and validates [file] against schema v1 (milestones.md §6.7.3/§6.7.4,
 * D84). Throws [org.gradle.api.GradleException] naming the failing rule and
 * the offending value on any violation — this function is the ENTIRE
 * validation surface; nothing downstream re-validates. */
fun loadOperatorConfig(file: java.io.File): OperatorConfig {
    // Rule 9: file missing entirely fails the build (this repo always
    // ships the committed reference config, so "missing" only ever means a
    // local checkout problem or a fork that deleted it).
    if (!file.exists()) {
        throw org.gradle.api.GradleException(
            "operator.json: rule 9 — no file at ${file.path}. Copy the committed " +
                "reference config (apps/scanner/operator.json in this repo) and adjust " +
                "its verifiers/thresholds/strings for your deployment."
        )
    }
    val text = file.readText()
    val parsed = try {
        JsonSlurper().parseText(text)
    } catch (e: Exception) {
        throw org.gradle.api.GradleException("operator.json: not valid JSON — ${e.message}")
    }
    if (parsed !is Map<*, *>) {
        throw org.gradle.api.GradleException("operator.json: top level must be a JSON object")
    }

    val allowedTopKeys = setOf(
        "schema_version", "operator", "tiers", "thresholds", "verifiers",
        "multi_threshold_verifiers", "evidence_plug", "tier_c", "tier_c_verifiers", "strings",
    )
    for (key in parsed.keys) {
        if (key !is String || key !in allowedTopKeys) {
            throw org.gradle.api.GradleException("operator.json: rule 8 — unknown top-level key '$key'")
        }
    }

    // Rule 1
    val schemaVersion = parsed["schema_version"]
    if (schemaVersion !is Number || schemaVersion.toInt() != 1) {
        throw org.gradle.api.GradleException("operator.json: rule 1 — schema_version missing or not 1 (got: $schemaVersion)")
    }

    val operatorObj = parsed["operator"]
    if (operatorObj !is Map<*, *>) {
        throw org.gradle.api.GradleException("operator.json: 'operator' must be an object")
    }
    for (key in operatorObj.keys) {
        if (key !is String || key !in setOf("name", "contact_url")) {
            throw org.gradle.api.GradleException("operator.json: rule 8 — unknown key 'operator.$key'")
        }
    }
    val operatorName = operatorObj["name"] as? String
        ?: throw org.gradle.api.GradleException("operator.json: operator.name must be a string")
    val contactUrl = operatorObj["contact_url"] as? String
        ?: throw org.gradle.api.GradleException("operator.json: operator.contact_url must be a string")

    // Rule 2
    val tiers = parsed["tiers"] as? String
    if (tiers != "A+B" && tiers != "B") {
        throw org.gradle.api.GradleException("operator.json: rule 2 — tiers must be exactly \"A+B\" or \"B\" (got: $tiers)")
    }

    // Rule 3
    val thresholdsRaw = parsed["thresholds"]
    if (thresholdsRaw !is List<*> || thresholdsRaw.isEmpty()) {
        throw org.gradle.api.GradleException("operator.json: rule 3 — thresholds must be a non-empty array")
    }
    val thresholds = thresholdsRaw.map { entry ->
        (entry as? Number)?.toInt()
            ?: throw org.gradle.api.GradleException("operator.json: rule 3 — thresholds entries must be integers (got: $entry)")
    }
    for (threshold in thresholds) {
        if (threshold !in ALLOWED_THRESHOLDS_D74) {
            throw org.gradle.api.GradleException(
                "operator.json: rule 3 — threshold $threshold is not one of $ALLOWED_THRESHOLDS_D74 (D74)"
            )
        }
    }

    // Rule 4 / rule 12 (item 23, D109(i)1): `verifiers` is EITHER a non-empty array of bare hostnames (rule 4 owns
    // every array-shape violation) OR exactly the string "any" (age verification open to any site). Anything else
    // is rule 12: a different string ("*", "ANY", "any ", ""), a number, an object, null/missing, or an array that
    // contains the entry "any" (a legal hostname label, so ["any"] must not be mistaken for the open form).
    val verifiersRaw = parsed["verifiers"]
    val verifiersAny: Boolean
    val verifiers: List<String>
    when {
        verifiersRaw == "any" -> {
            verifiersAny = true
            verifiers = emptyList()
        }
        verifiersRaw is List<*> -> {
            if (verifiersRaw.isEmpty()) {
                throw org.gradle.api.GradleException("operator.json: rule 4 — verifiers must be a non-empty array")
            }
            verifiers = verifiersRaw.map { entry ->
                entry as? String ?: throw org.gradle.api.GradleException("operator.json: rule 4 — verifiers entries must be strings")
            }
            if (verifiers.any { it.trim().lowercase() == "any" }) {
                throw org.gradle.api.GradleException(
                    "operator.json: rule 12 — verifiers array contains the entry \"any\"; the open form is the bare string " +
                        "\"verifiers\": \"any\", not an array entry"
                )
            }
            for (verifier in verifiers) {
                if (!isBareHostname(verifier)) {
                    throw org.gradle.api.GradleException(
                        "operator.json: rule 4 — verifiers entry '$verifier' must be a bare hostname (no scheme, no port, no wildcard)"
                    )
                }
            }
            verifiersAny = false
        }
        else -> throw org.gradle.api.GradleException(
            "operator.json: rule 12 — verifiers must be a non-empty array of bare hostnames or exactly the string \"any\" (got: $verifiersRaw)"
        )
    }

    // Rule 5 (default [])
    val multiThresholdRaw = parsed["multi_threshold_verifiers"] ?: emptyList<Any>()
    if (multiThresholdRaw !is List<*>) {
        throw org.gradle.api.GradleException("operator.json: rule 5 — multi_threshold_verifiers must be an array")
    }
    val multiThresholdVerifiers = multiThresholdRaw.map { entry ->
        entry as? String
            ?: throw org.gradle.api.GradleException("operator.json: rule 5 — multi_threshold_verifiers entries must be strings")
    }
    for (verifier in multiThresholdVerifiers) {
        if (!isBareHostname(verifier)) {
            throw org.gradle.api.GradleException(
                "operator.json: rule 5 — multi_threshold_verifiers entry '$verifier' must be a bare hostname (no scheme, no port, no wildcard)"
            )
        }
    }

    // Rule 6
    val evidencePlug = parsed["evidence_plug"] as? String
    if (evidencePlug == null || evidencePlug !in ALLOWED_EVIDENCE_PLUGS) {
        throw org.gradle.api.GradleException(
            "operator.json: rule 6 — evidence_plug must be one of $ALLOWED_EVIDENCE_PLUGS (got: $evidencePlug)"
        )
    }

    // Rule 7 (default []) — RELAXED by M3b (D109(b), §6.9.2 item 2): was "must be empty"; now an array
    // of bare hostnames, exactly like `verifiers` (no scheme, port, wildcard, comma, quote, backslash).
    val tierCVerifiersRaw = parsed["tier_c_verifiers"] ?: emptyList<Any>()
    if (tierCVerifiersRaw !is List<*>) {
        throw org.gradle.api.GradleException("operator.json: rule 7 — tier_c_verifiers must be an array")
    }
    val tierCVerifiers = tierCVerifiersRaw.map { entry ->
        entry as? String
            ?: throw org.gradle.api.GradleException("operator.json: rule 7 — tier_c_verifiers entries must be strings")
    }
    for (verifier in tierCVerifiers) {
        if (!isBareHostname(verifier)) {
            throw org.gradle.api.GradleException(
                "operator.json: rule 7 — tier_c_verifiers entry '$verifier' must be a bare hostname (no scheme, no port, no wildcard)"
            )
        }
    }

    // tier_c switch (§6.9.2 item 1, D96(1)): `{ "enabled": false }`, default false/absent. Unknown keys are
    // rule 8's business, like every other object in schema v1.
    val tierCObj = parsed["tier_c"] ?: emptyMap<String, Any>()
    if (tierCObj !is Map<*, *>) {
        throw org.gradle.api.GradleException("operator.json: tier_c must be an object")
    }
    for (key in tierCObj.keys) {
        if (key !is String || key != "enabled") {
            throw org.gradle.api.GradleException("operator.json: rule 8 — unknown key 'tier_c.$key'")
        }
    }
    val tierCEnabledRaw = tierCObj["enabled"] ?: false
    if (tierCEnabledRaw !is Boolean) {
        throw org.gradle.api.GradleException("operator.json: tier_c.enabled must be true or false (got: $tierCEnabledRaw)")
    }
    val tierCEnabled: Boolean = tierCEnabledRaw

    // Rule 10 / rule 11 (D109(g)7): the two tier-C knobs may never disagree silently.
    if (tierCEnabled && tierCVerifiers.isEmpty()) {
        throw org.gradle.api.GradleException(
            "operator.json: rule 10 — tier_c.enabled is true but tier_c_verifiers is empty (a tier-C build must pin at least one site)"
        )
    }
    if (!tierCEnabled && tierCVerifiers.isNotEmpty()) {
        throw org.gradle.api.GradleException(
            "operator.json: rule 11 — tier_c_verifiers is non-empty but tier_c.enabled is not true (the pin list is meaningless with the switch off)"
        )
    }

    // Rule 8 (strings.*) + strings.app_name
    val stringsObj = parsed["strings"]
    if (stringsObj !is Map<*, *>) {
        throw org.gradle.api.GradleException("operator.json: 'strings' must be an object")
    }
    for (key in stringsObj.keys) {
        if (key !is String || key != "app_name") {
            throw org.gradle.api.GradleException(
                "operator.json: rule 8 — unknown key 'strings.$key' (only app_name is permitted in schema v1, D84 point 3)"
            )
        }
    }
    val appName = stringsObj["app_name"] as? String
        ?: throw org.gradle.api.GradleException("operator.json: strings.app_name must be a string")

    return OperatorConfig(
        operatorName = operatorName,
        contactUrl = contactUrl,
        tiers = tiers,
        thresholds = thresholds,
        verifiersAny = verifiersAny,
        verifiers = verifiers,
        multiThresholdVerifiers = multiThresholdVerifiers,
        evidencePlug = evidencePlug,
        tierCEnabled = tierCEnabled,
        tierCVerifiers = tierCVerifiers,
        appName = appName,
    )
}

// operator.json lives at the module root (apps/scanner/), one level up from
// this app/ subproject's build.gradle.kts — same scoping `runGitCommand`'s
// dirty-check below already uses for `projectDir.parentFile`.
//
// M3b POC: `-PoperatorJson=<path>` builds against a different operator.json (an absolute path, or one
// relative to apps/scanner/) so the tier-C device runs can produce a switch-on/pinned APK and a
// switch-off APK without editing the committed reference file. Unset, nothing changes.
val operatorJsonOverride: String? = providers.gradleProperty("operatorJson").orNull
val operatorConfig: OperatorConfig = loadOperatorConfig(
    if (operatorJsonOverride == null) {
        file("${projectDir.parentFile}/operator.json")
    } else {
        rootProject.file(operatorJsonOverride) // absolute, or relative to apps/scanner/
    }
)

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
        versionCode = 8
        // D72 (2026-09-03): lockstep versioning — versionName now tracks the
        // repo's single release version (packages/chiproof/package.json +
        // this field + one CHANGELOG.md section, bumped together every
        // release), not an app-only number.
        versionName = "0.9.0"

        // §6.2 item 24 (D70(c)) — exposed as BuildConfig.GIT_SHA, consumed
        // by VersionStamp.format(). Not a manifest label: a manifest
        // placeholder can't feed both the scan-pane footer and each log
        // entry's technical line from one source the way a BuildConfig
        // constant can.
        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")

        // §6.7 POC — operator.json's validated fields, exposed as
        // BuildConfig constants. Lists are comma-joined Strings (see
        // OperatorConfig's doc); OperatorPolicy.kt is the ONE place that
        // reads these back and splits/trims/lowercases them — no other
        // file may reference BuildConfig.OPERATOR_* directly.
        buildConfigField("String", "OPERATOR_NAME", "\"${escapeForStringLiteral(operatorConfig.operatorName)}\"")
        buildConfigField("String", "OPERATOR_CONTACT_URL", "\"${escapeForStringLiteral(operatorConfig.contactUrl)}\"")
        buildConfigField("String", "OPERATOR_TIERS", "\"${escapeForStringLiteral(operatorConfig.tiers)}\"")
        buildConfigField("String", "OPERATOR_THRESHOLDS", "\"${operatorConfig.thresholds.joinToString(",")}\"")
        // Item 23: the open form is its own boolean, never a hostname-list entry (D86's comma-split hazard); when it is
        // true the hostname list below is empty. OperatorPolicy is the one reader of both.
        buildConfigField("boolean", "OPERATOR_VERIFIERS_ANY", operatorConfig.verifiersAny.toString())
        buildConfigField(
            "String", "OPERATOR_VERIFIERS",
            "\"${operatorConfig.verifiers.joinToString(",") { escapeForStringLiteral(it) }}\"",
        )
        buildConfigField(
            "String", "OPERATOR_MULTI_THRESHOLD_VERIFIERS",
            "\"${operatorConfig.multiThresholdVerifiers.joinToString(",") { escapeForStringLiteral(it) }}\"",
        )
        buildConfigField("String", "OPERATOR_EVIDENCE_PLUG", "\"${escapeForStringLiteral(operatorConfig.evidencePlug)}\"")
        buildConfigField("boolean", "OPERATOR_TIER_C_ENABLED", operatorConfig.tierCEnabled.toString())
        buildConfigField(
            "String", "OPERATOR_TIER_C_VERIFIERS",
            "\"${operatorConfig.tierCVerifiers.joinToString(",") { escapeForStringLiteral(it) }}\"",
        )

        // §6.7 item 6 (app_name) — the ONE writer of the app_name resource;
        // apps/scanner/app/src/main/res/values/strings.xml no longer
        // declares it (see that file's own note). resValue's generated
        // entry has LOWER priority than any source-set resource, so the
        // existing debug-variant override
        // (src/debug/res/values/strings.xml, "zkagent Scanner (Debug)")
        // still wins for debug builds, unaffected by this change.
        resValue("string", "app_name", operatorConfig.appName)
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
