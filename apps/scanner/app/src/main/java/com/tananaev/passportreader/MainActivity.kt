/*
 * Copyright 2016 - 2022 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.tananaev.passportreader

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import net.sf.scuba.smartcards.CardService
import org.apache.commons.io.IOUtils
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG1File
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.Signature
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * zkagent M2 reference scanner — PRD `docs/product/zkagent-prd.md` §6.2,
 * items 1-11. A REWRITE, not a graduated spike: drawn from
 * `spikes/m2-scan` (masterlist/passive-auth read path) and
 * `spikes/m2-session-poc` (StrongBox key + biometric composition, item 12's
 * already-PASSED POC), but restructured where the PRD requires it —
 * see the per-item notes below and the conformance report.
 *
 * ---------------------------------------------------------------------
 * §6.2 item 4 (F5, the mode-radio bug) — STRUCTURAL fix, not a root-cause:
 * ---------------------------------------------------------------------
 * F5 (`docs/logs/M2-SCAN-EVIDENCE.md`: the mode radio displayed "B" once
 * while a scan actually ran in mode A) was never root-caused in
 * `spikes/m2-scan`'s `MainActivity.kt` — a code read after the fact found
 * no cause. Rather than assume a rewrite fixes an unreproduced bug by
 * construction, this file removes the SURFACE the bug needs to exist:
 * [modeGroup.checkedRadioButtonId] is read from the UI EXACTLY ONCE in this
 * entire file, inside [lockModeAndArm], which runs BEFORE NFC foreground
 * dispatch is even enabled — grep `checkedRadioButtonId` against this file
 * and there is one call site. The result is copied into [lockedMode], an
 * immutable `val` set once per session and cleared only by [wipeSession];
 * every other place that needs the mode (the read report, the mint gate,
 * the evidence tier) reads [lockedMode], never the RadioGroup. Because the
 * RadioGroup is also disabled the instant it is read (`modeGroup.isEnabled
 * = false`), it is not just unread after that point, it is unwritable by
 * the user either — a UI/session-state mismatch of F5's shape cannot occur
 * because there is no window in which the control can change AND still be
 * consulted.
 *
 * ---------------------------------------------------------------------
 * §6.2 item 2/3 (D21 "always read, conditionally mint") — ordering:
 * ---------------------------------------------------------------------
 * Unlike `spikes/m2-session-poc`'s item-12 POC (which deliberately put the
 * biometric prompt BEFORE the chip read, to stress-test whether the IsoDep
 * session survives that interruption — that POC PASSED and is not re-run
 * here), this file reads the chip UNCONDITIONALLY first: PACE/BAC, DG1+SOD,
 * masterlist-gated passive auth. The Keystore/biometric step happens
 * strictly AFTER, and ONLY when [lockedMode] is B AND `passiveAuth.ok &&
 * passiveAuth.allowed == true` (item 3) — at which point the IsoDep tag is
 * no longer needed at all (signing is Keystore-only), so it is closed
 * before the biometric prompt ever shows. Mode A never shows the biometric
 * prompt (D27: mode A ships bare, `evidence: []`) and a masterlist real-no
 * shows it either (item 3: "MUST derive and emit no zktag").
 *
 * ---------------------------------------------------------------------
 * §6.2 item 5 — no ResultActivity, no field rendering, anywhere:
 * ---------------------------------------------------------------------
 * [reportView] is value-free by construction the same way
 * `spikes/m2-session-poc` proved it could be: verdict booleans, step names,
 * counts, hashes, algorithm names, timings. Grep-provable: `mrzInfo`,
 * `secondaryIdentifier`, `primaryIdentifier` (the M0-era field-rendering
 * call sites) do not appear in this file.
 *
 * ---------------------------------------------------------------------
 * §6.2 item 6 — lifecycle:
 * ---------------------------------------------------------------------
 * [wipeSession] runs in `onStop()`, never `onPause()` (NFC foreground
 * dispatch pauses/resumes this still-visible activity before `onNewIntent`
 * delivers the tag). MRZ text AND [lockedMode] are KEPT only on an
 * access-establishment failure (PACE/BAC `SW 0x6300`->`0x6985`), so a
 * mistyped key is a retry, not a full re-lock+retype (F3); every other
 * outcome wipes both.
 *
 * ---------------------------------------------------------------------
 * §6.2 items 13/14 (D33/D34/D37) — handoff trust, fetched EARLY:
 * ---------------------------------------------------------------------
 * A pending handoff's request object is fetched and verified
 * ([RequestTrust]: origin consistency, then ES256 JWS signature) the
 * INSTANT the `av://`/QR/pasted link is captured ([beginHandoffVerification]),
 * on a background thread — not later, inside the mint path, the way this
 * file used to. The verified result lives in [verifiedRequest], alongside
 * [pendingHandoff], for the rest of that handoff's lifetime. This is what
 * lets [lockModeAndArm] (item 4's ONE call site for [lockedMode]) preset and
 * lock the mode from the request's `zkagent.tier` BEFORE the user can touch
 * the mode radio — [lockedMode] still has exactly one writer, it is just fed
 * from [verifiedRequest] instead of [modeGroup] when a handoff is pending.
 * [applyHandoffVerificationOutcome] also calls `modeGroup.check(...)` on a
 * successful verify, so the RESULT is visible (D33: the app "sets" the
 * mode) the instant verification succeeds, not only once Lock is pressed —
 * this is a DISPLAY write, not a read: [modeGroup.checkedRadioButtonId] is
 * still read from the UI in exactly the one place item 4 names
 * ([lockModeAndArm]), and the control is disabled the whole time
 * ([beginHandoffVerification] disables it before this write ever happens),
 * so the user still can't touch it. A tier this preview can't map (C,
 * absent, invalid) clears the check instead of guessing — the actual
 * refusal is reported once the user tries to lock (item 13's fail-loud path).
 * [mintAndMaybeHandoff] later REUSES [verifiedRequest] rather than
 * re-fetching — same nonce, same verified fields, one fetch per handoff.
 * Any verification failure (origin mismatch, bad/missing/wrong-alg
 * signature, no resolvable key) is a refusal: [pendingHandoff] and
 * [verifiedRequest] are cleared, the mode radio is re-enabled for manual
 * use (no handoff is pending any more), and the refusal is logged AND
 * reported — never a silent downgrade to trusting the fields anyway.
 */
abstract class MainActivity : AppCompatActivity() {

    // ---- presentation mode: see class doc item 4 ----
    private enum class PresentationMode { A, B }
    /** Set ONLY by [lockModeAndArm]. Read everywhere else that needs mode. */
    private var lockedMode: PresentationMode? = null

    // ---- handoff: see class doc item 8 / HandoffClient ----
    private var pendingHandoff: HandoffClient.PendingHandoff? = null

    // ---- verified handoff request object: see class doc items 13/14 ----
    // Set ONLY by [applyHandoffVerificationOutcome] on a successful verify;
    // cleared alongside [pendingHandoff] everywhere that is cleared.
    private var verifiedRequest: RequestTrust.VerifiedRequest? = null

    // ---- last rendered report, for onSaveInstanceState only — see
    // [emitReport] and the item 6 note there. Never written to disk. ----
    private var lastReportText: String? = null

    private lateinit var passportNumberView: EditText
    private lateinit var expirationDateView: EditText
    private lateinit var birthDateView: EditText
    private lateinit var modeGroup: RadioGroup
    private lateinit var modeLockedBanner: TextView
    private lateinit var lockButton: Button
    private lateinit var mainLayout: View
    private lateinit var loadingLayout: View
    private lateinit var reportView: TextView
    private lateinit var handoffStatus: TextView
    private lateinit var handoffManualInput: EditText

    private val qrCaptureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap == null) {
            Snackbar.make(reportView, "QR capture cancelled", Snackbar.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val text = QrCapture.decode(bitmap)
        if (text == null) {
            Snackbar.make(reportView, "No QR code found in that photo — try again", Snackbar.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        applyPendingHandoffText(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        passportNumberView = findViewById(R.id.input_passport_number)
        expirationDateView = findViewById(R.id.input_expiration_date)
        birthDateView = findViewById(R.id.input_date_of_birth)
        modeGroup = findViewById(R.id.mode_group)
        modeLockedBanner = findViewById(R.id.mode_locked_banner)
        lockButton = findViewById(R.id.button_lock_and_scan)
        mainLayout = findViewById(R.id.main_layout)
        loadingLayout = findViewById(R.id.loading_layout)
        reportView = findViewById(R.id.report_view)
        handoffStatus = findViewById(R.id.handoff_status)
        handoffManualInput = findViewById(R.id.handoff_manual_input)

        // No SharedPreferences/DataStore read or write anywhere in this
        // activity — MRZ fields start empty every launch (NO-GO #9).

        // ESCALATION (flagged for owner, see final report): restores the
        // last value-free report text ONLY, across an Activity re-creation
        // (config change / background memory reclaim) — an in-memory Bundle
        // via onSaveInstanceState, never disk. wipeSession() never touches
        // reportView, so within one Activity instance the report already
        // survived onStop; this additionally survives the instance itself
        // being destroyed and recreated, which is what actually looked like
        // "onStop wiped the report" from the UI.
        savedInstanceState?.getString(STATE_LAST_REPORT)?.let { text ->
            lastReportText = text
            reportView.text = text
        }

        lockButton.setOnClickListener { lockModeAndArm() }

        findViewById<View>(R.id.button_m2_masterlist_probe).setOnClickListener {
            Thread { runMasterlistProbe() }.start()
        }

        findViewById<View>(R.id.button_devicekey_probe).setOnClickListener {
            Thread { runDeviceKeyProbe() }.start()
        }
        // DEBUG-BUILD-ONLY, owner-approved this session: long-press KEY TEST
        // exports the CURRENT device attester public key (+ key_id) to
        // filesDir for the owner to pin into the spikes/m2-handoff dev
        // verifier — see DeviceKey.exportDevAttesterPublicKeyIfPresent's doc
        // for the value-free logging discipline and the no-key-as-side-effect
        // rule. Listener not even attached outside a debug build — belt and
        // suspenders alongside that function's own BuildConfig.DEBUG guard.
        if (BuildConfig.DEBUG) {
            findViewById<View>(R.id.button_devicekey_probe).setOnLongClickListener {
                Thread { DeviceKey.exportDevAttesterPublicKeyIfPresent(applicationContext) }.start()
                true
            }
        }

        findViewById<View>(R.id.button_scan_qr).setOnClickListener {
            qrCaptureLauncher.launch(null)
        }
        handoffManualInput.setOnEditorActionListener { _, _, _ ->
            applyPendingHandoffText(handoffManualInput.text?.toString().orEmpty())
            true
        }

        expirationDateView.setOnClickListener {
            val c = loadDate(expirationDateView)
            val dialog = DatePickerDialog.newInstance(
                { _, year, monthOfYear, dayOfMonth ->
                    expirationDateView.setText(String.format(Locale.US, "%d-%02d-%02d", year, monthOfYear + 1, dayOfMonth))
                },
                c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH],
            )
            dialog.showYearPickerFirst(true)
            supportFragmentManager.beginTransaction().add(dialog, null).commit()
        }
        birthDateView.setOnClickListener {
            val c = loadDate(birthDateView)
            val dialog = DatePickerDialog.newInstance(
                { _, year, monthOfYear, dayOfMonth ->
                    birthDateView.setText(String.format(Locale.US, "%d-%02d-%02d", year, monthOfYear + 1, dayOfMonth))
                },
                c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH],
            )
            dialog.showYearPickerFirst(true)
            supportFragmentManager.beginTransaction().add(dialog, null).commit()
        }

        handleIncomingIntent(intent)
    }

    private fun setModeGroupEnabled(enabled: Boolean) {
        modeGroup.isEnabled = enabled
        for (i in 0 until modeGroup.childCount) modeGroup.getChildAt(i).isEnabled = enabled
    }

    /** §6.2 item 13 (D33): the mode a pending, VERIFIED handoff request
     * demands — `zkagent.tier`, parsed no more than this one place. Returns
     * null (absent/invalid) or throws-shaped via [TierOutcome.Unsupported]/
     * [TierOutcome.Invalid] for the two failure cases item 13 distinguishes:
     * an unsupported-but-well-formed tier (C) vs. anything else absent/bad. */
    private sealed class TierOutcome {
        data class Ok(val mode: PresentationMode) : TierOutcome()
        object Unsupported : TierOutcome() // tier C — well-formed, not built yet
        data class Invalid(val got: String) : TierOutcome() // absent or not A/B/C
    }

    private fun tierOutcomeFor(verified: RequestTrust.VerifiedRequest): TierOutcome {
        val tier = RequestTrust.tierOf(verified.json)
        return when (tier) {
            "A" -> TierOutcome.Ok(PresentationMode.A)
            "B" -> TierOutcome.Ok(PresentationMode.B)
            "C" -> TierOutcome.Unsupported
            else -> TierOutcome.Invalid(tier ?: "<absent>")
        }
    }

    // ---------------------------------------------------------------- item 4
    /** The ONE call site in this file that reads [modeGroup] OR
     * [verifiedRequest] to decide mode. Disables the control the same
     * instant it is consulted, then arms NFC dispatch. §6.2 item 13 (D33):
     * when a handoff is pending, [lockedMode] comes from the verified
     * request's `zkagent.tier` instead of the RadioGroup — never both, and
     * never a second call site. */
    private fun lockModeAndArm() {
        if (lockedMode != null) return // already locked this session
        val passportRaw = passportNumberView.text?.toString()
        val expirationRaw = expirationDateView.text?.toString()
        val birthRaw = birthDateView.text?.toString()
        if (passportRaw.isNullOrEmpty() || expirationRaw.isNullOrEmpty() || birthRaw.isNullOrEmpty()) {
            Snackbar.make(passportNumberView, R.string.error_input, Snackbar.LENGTH_SHORT).show()
            return
        }

        val handoff = pendingHandoff
        val mode: PresentationMode
        if (handoff != null) {
            val verified = verifiedRequest
            if (verified == null) {
                Snackbar.make(passportNumberView, "Still verifying the handoff request — try again in a moment", Snackbar.LENGTH_SHORT).show()
                return
            }
            when (val outcome = tierOutcomeFor(verified)) {
                is TierOutcome.Ok -> mode = outcome.mode
                is TierOutcome.Unsupported -> {
                    Log.e(TAG, "M2 stage: pending handoff requests tier C — not supported in this build (item 13)")
                    emitReport("handoff: REFUSED — tier C requested, not supported in this build (no tier-C flow)")
                    return
                }
                is TierOutcome.Invalid -> {
                    Log.e(TAG, "M2 stage: pending handoff request has absent/invalid tier (got: ${outcome.got}) — refusing, no default mode (item 13)")
                    emitReport("handoff: REFUSED — request tier absent or invalid (got: ${outcome.got}), no default mode")
                    return
                }
            }
            modeGroup.check(if (mode == PresentationMode.B) R.id.mode_b else R.id.mode_a)
        } else {
            mode = if (modeGroup.checkedRadioButtonId == R.id.mode_b) PresentationMode.B else PresentationMode.A
        }

        lockedMode = mode
        setModeGroupEnabled(false)
        lockButton.isEnabled = false
        modeLockedBanner.visibility = View.VISIBLE
        modeLockedBanner.text = "Locked: mode ${lockedMode} — tap your document now"
        armNfcDispatch()
    }

    private fun armNfcDispatch() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        val intent = Intent(applicationContext, this.javaClass).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        adapter.enableForegroundDispatch(this, pendingIntent, null, arrayOf(arrayOf("android.nfc.tech.IsoDep")))
    }

    override fun onResume() {
        super.onResume()
        if (lockedMode != null) armNfcDispatch() // e.g. returning from the biometric prompt / a backgrounding
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
        // No wipeSession() here — see class doc item 6 / F2 (m2-scan finding).
    }

    override fun onStop() {
        super.onStop()
        wipeSession(keepMrzAndMode = false)
        // wipeSession() intentionally does not touch reportView — the
        // value-free report (item 5) stays visible; only MRZ + locked mode +
        // keys-in-flight (item 6's "session state") are wiped. See emitReport.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastReportText?.let { outState.putString(STATE_LAST_REPORT, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            val handoff = HandoffClient.parseAvLink(data)
            if (handoff != null) {
                Log.i(TAG, "M2 stage: pendingHandoff captured from av:// intent")
                beginHandoffVerification(handoff)
                return
            }
        }
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: Tag? = intent.extras?.getParcelable(NfcAdapter.EXTRA_TAG)
            if (tag?.techList?.contains("android.nfc.tech.IsoDep") == true) {
                val mode = lockedMode
                if (mode == null) {
                    Log.i(TAG, "M2 stage: ignoring tag intent — no locked mode (dispatch should not have been armed)")
                    return
                }
                val passportRaw = passportNumberView.text?.toString()
                val expirationRaw = convertDate(expirationDateView.text?.toString())
                val birthRaw = convertDate(birthDateView.text?.toString())
                if (passportRaw.isNullOrEmpty() || expirationRaw == null || birthRaw == null) {
                    Log.i(TAG, "M2 stage: ignoring tag intent — MRZ fields empty (stale/re-delivered intent)")
                    return
                }
                val bacKey: BACKeySpec = BACKey(passportRaw, birthRaw, expirationRaw)
                startSession(IsoDep.get(tag), bacKey, mode)
            }
        }
    }

    private fun applyPendingHandoffText(text: String) {
        val handoff = HandoffClient.parsePastedText(text)
        if (handoff == null) {
            Snackbar.make(reportView, "Not a recognised av:// link or request_uri", Snackbar.LENGTH_LONG).show()
            return
        }
        handoffManualInput.text?.clear()
        beginHandoffVerification(handoff)
    }

    // ------------------------------------------------------- items 13/14
    /** Fetches and verifies [handoff]'s request object ([RequestTrust]) on a
     * background thread, the INSTANT the link is captured — see class doc.
     * The mode radio and Lock button are held off until this resolves, so
     * there is no window where the user could lock a session against an
     * unverified/wrong tier. */
    private fun beginHandoffVerification(handoff: HandoffClient.PendingHandoff) {
        pendingHandoff = handoff
        verifiedRequest = null
        handoffStatus.text = "Handoff request received — verifying signature and origin…"
        setModeGroupEnabled(false)
        lockButton.isEnabled = false
        Log.i(TAG, "M2 stage: handoff captured, verifying request object before mode/lock become available (D33/D34/D37)")
        Thread {
            val outcome = try {
                verifyPendingHandoff(handoff)
            } catch (e: Exception) {
                Log.e(TAG, "M2 stage: handoff verification threw", e)
                RequestTrust.Outcome.Refused("verification threw ${e.javaClass.simpleName}: ${e.message}")
            }
            runOnUiThread { applyHandoffVerificationOutcome(handoff, outcome) }
        }.start()
    }

    /** §6.2 item 14 (D34/D37), in order: (1) `client_id`/`request_uri` origin
     * match, before anything else; (2) resolve a trusted key for that
     * origin (dev-pinned for `http://127.0.0.1`/`localhost`, well-known
     * fetch otherwise); (3) GET the request object and verify its ES256 JWS
     * against that key; (4) the verified payload's OWN `response_uri` must
     * resolve to the SAME origin as (1). Any failure refuses — see class doc. */
    private fun verifyPendingHandoff(handoff: HandoffClient.PendingHandoff): RequestTrust.Outcome {
        val requestUriOrigin = RequestTrust.originOf(handoff.requestUri)
            ?: return RequestTrust.Outcome.Refused("request_uri has no parseable origin: ${handoff.requestUri}")
        val clientIdOrigin = handoff.clientId?.let {
            RequestTrust.originOf(it) ?: return RequestTrust.Outcome.Refused("client_id has no parseable origin: $it")
        }
        if (clientIdOrigin != null && clientIdOrigin != requestUriOrigin) {
            return RequestTrust.Outcome.Refused("origin mismatch: client_id=$clientIdOrigin request_uri=$requestUriOrigin")
        }
        val origin = requestUriOrigin

        val key = RequestTrust.resolveVerifierKey(origin)
            ?: return RequestTrust.Outcome.Refused("no trusted request-signer key resolvable for origin $origin")

        val raw = try {
            HandoffClient.fetchRequestRaw(handoff.requestUri)
        } catch (e: HandoffClient.HandoffHttpException) {
            return RequestTrust.Outcome.Refused("request_uri fetch failed: HTTP ${e.httpStatus}: ${e.message}")
        } catch (e: Exception) {
            return RequestTrust.Outcome.Refused("request_uri fetch failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        val verified = RequestTrust.verifyRequestObject(raw.body, key)
        val payload = verified.payload
        if (!verified.ok || payload == null) {
            return RequestTrust.Outcome.Refused("JWS verification failed: ${verified.reason}")
        }

        val responseUriStr = payload.optString("response_uri", "").ifEmpty { null }
            ?: return RequestTrust.Outcome.Refused("verified request object carries no response_uri")
        val responseUriOrigin = RequestTrust.originOf(responseUriStr)
            ?: return RequestTrust.Outcome.Refused("response_uri has no parseable origin: $responseUriStr")
        if (responseUriOrigin != origin) {
            return RequestTrust.Outcome.Refused("origin mismatch: response_uri=$responseUriOrigin request_uri=$requestUriOrigin")
        }

        Log.i(TAG, "M2 stage: handoff request object verified — origin=$origin signature_verified=true key_kind=${if (key.isDev) "dev-pinned" else "well-known"}")
        return RequestTrust.Outcome.Verified(RequestTrust.VerifiedRequest(payload, origin))
    }

    /** Applies [outcome] on the UI thread. A superseded in-flight
     * verification (a newer handoff replaced [pendingHandoff] before this
     * one resolved) is dropped — never allowed to clobber a fresher state. */
    private fun applyHandoffVerificationOutcome(handoff: HandoffClient.PendingHandoff, outcome: RequestTrust.Outcome) {
        if (pendingHandoff !== handoff) {
            Log.i(TAG, "M2 stage: dropping a superseded handoff verification result")
            return
        }
        when (outcome) {
            is RequestTrust.Outcome.Verified -> {
                verifiedRequest = outcome.request
                val rawTier = RequestTrust.tierOf(outcome.request.json)
                handoffStatus.text = "Handoff verified — origin: ${outcome.request.origin}, requested tier: ${rawTier ?: "<absent>"}. Fill in your document details and lock to answer it."
                // §6.2 item 13 (D33): SHOW the mode the request set the instant
                // verification succeeds — a display write, not the read item 4
                // guards (see class doc addendum above). lockModeAndArm() is
                // still the only place that turns this into lockedMode. C/
                // absent/invalid tiers clear the check rather than guess; the
                // fail-loud refusal happens when the user tries to lock.
                when (rawTier) {
                    "A" -> modeGroup.check(R.id.mode_a)
                    "B" -> modeGroup.check(R.id.mode_b)
                    else -> modeGroup.clearCheck()
                }
                lockButton.isEnabled = true
                // Mode radio stays disabled: item 13 forbids user override once a handoff is pending.
            }
            is RequestTrust.Outcome.Refused -> {
                Log.e(TAG, "M2 stage: handoff REFUSED — ${outcome.reason}")
                emitReport("handoff: REFUSED — ${outcome.reason}")
                pendingHandoff = null
                verifiedRequest = null
                handoffStatus.text = "Handoff refused (${outcome.reason}) — you may still scan manually."
                setModeGroupEnabled(true)
                lockButton.isEnabled = true
            }
        }
    }

    /** §6.2 item 6: MRZ + [lockedMode] are kept ONLY on an access-establishment
     * failure. Every other case (success, or a later-stage failure) wipes both.
     * §6.2 item 13: if a VERIFIED handoff is still pending across a retry, the
     * mode radio stays locked/disabled — a failed read must not reopen manual
     * mode selection out from under a still-pending handoff. */
    private fun wipeSession(keepMrzAndMode: Boolean) {
        if (!keepMrzAndMode) {
            passportNumberView.text?.clear()
            expirationDateView.text?.clear()
            birthDateView.text?.clear()
            lockedMode = null
            modeLockedBanner.visibility = View.GONE
            lockButton.isEnabled = true
            setModeGroupEnabled(pendingHandoff == null || verifiedRequest == null)
        }
    }

    // ---------------------------------------------------------------------
    /** §6.2 items 5/6 — the ONE place a report is ever rendered to
     * [reportView]. Every terminal outcome (success, failure, refusal,
     * gate-not-met, exception) and every intermediate progress state MUST go
     * through this function and ONLY this function — see [MintGate]'s doc
     * for why: the 2026-08-31 stall (runs 2/3 producing an on-screen verdict
     * with ZERO logcat trace) was exactly a `reportView.text = ...` site
     * that never called `Log.i`. Value-free by construction — callers pass
     * only verdict booleans, step names, counts, hashes, algorithm names,
     * timings; never MRZ/DG1 field values or the raw zktag. */
    private fun emitReport(text: String) {
        reportView.text = text
        lastReportText = text
        Log.i(TAG, "\n===== M2 REPORT (value-free) =====\n$text\n===== END =====")
    }

    // ------------------------------------------------------------- session
    private fun startSession(isoDep: IsoDep, bacKey: BACKeySpec, mode: PresentationMode) {
        mainLayout.visibility = View.GONE
        loadingLayout.visibility = View.VISIBLE
        ReadTask(isoDep, bacKey, mode).execute()
    }

    private inner class ReadTask(
        private val isoDep: IsoDep,
        private val bacKey: BACKeySpec,
        private val mode: PresentationMode,
    ) : AsyncTask<Void?, Void?, Exception?>() {

        private lateinit var dg1File: DG1File
        private lateinit var sodFile: SODFile
        private var accessFailure = false
        private val timeline = M0Probe.Timeline()
        private var accessProtocol = "unknown"
        private var chipAuthField = "absent"
        private var passiveAuthVerdict: M0Probe.Verdict? = null
        private var masterlistReport = ""
        private var trustedKeystoreCerts: java.security.KeyStore? = null

        override fun doInBackground(vararg params: Void?): Exception? {
            try {
                isoDep.timeout = 10000
                if (!isoDep.isConnected) isoDep.connect()
                val cardService = CardService.getInstance(isoDep)
                cardService.open()
                val service = PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, false, false)
                service.open()

                var paceSucceeded = false
                try {
                    val cardAccessFile = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
                    for (securityInfo: SecurityInfo in cardAccessFile.securityInfos) {
                        if (securityInfo is PACEInfo) {
                            service.doPACE(bacKey, securityInfo.objectIdentifier, PACEInfo.toParameterSpec(securityInfo.parameterId), null)
                            paceSucceeded = true
                        }
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "M2 stage: PACE unavailable (${e.javaClass.simpleName})")
                }

                try {
                    service.sendSelectApplet(paceSucceeded)
                    if (!paceSucceeded) {
                        try {
                            service.getInputStream(PassportService.EF_COM).read()
                        } catch (e: Exception) {
                            service.doBAC(bacKey)
                        }
                    }
                } catch (e: Exception) {
                    accessFailure = true // §6.2 item 6: keep MRZ+mode on this specific failure
                    throw e
                }
                accessProtocol = if (paceSucceeded) "PACE" else "BAC"
                timeline.mark("access_established ($accessProtocol)")

                val dg1Encoded = IOUtils.toByteArray(service.getInputStream(PassportService.EF_DG1))
                dg1File = DG1File(ByteArrayInputStream(dg1Encoded))
                val sodEncoded = IOUtils.toByteArray(service.getInputStream(PassportService.EF_SOD))
                sodFile = SODFile(ByteArrayInputStream(sodEncoded))
                timeline.mark("dg1_and_sod_read")

                // Chip authenticity probe (D21 payload field), best-effort —
                // absence is a finding, not a session failure.
                var chipAuthOk = false
                try {
                    val dg14In = service.getInputStream(PassportService.EF_DG14)
                    val dg14Encoded = IOUtils.toByteArray(dg14In)
                    val dg14File = org.jmrtd.lds.icao.DG14File(ByteArrayInputStream(dg14Encoded))
                    for (si in dg14File.securityInfos) {
                        if (si is org.jmrtd.lds.ChipAuthenticationPublicKeyInfo) {
                            service.doEACCA(si.keyId, org.jmrtd.lds.ChipAuthenticationPublicKeyInfo.ID_CA_ECDH_AES_CBC_CMAC_256, si.objectIdentifier, si.subjectPublicKey)
                            chipAuthOk = true
                        }
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "M2 stage: CA unavailable (${e.javaClass.simpleName})")
                }
                val aa = M0Probe.tryActiveAuth(service, sodFile)
                chipAuthField = if (chipAuthOk || aa.first) "passed" else "absent"
                timeline.mark("chip_auth_probed")

                // Chip session no longer needed past this point — close before
                // any Keystore/biometric work (item 2/3 ordering, class doc).
                try { isoDep.close() } catch (e: Exception) { Log.i(TAG, "M2 stage: isoDep.close() threw (benign, already closing): ${e.javaClass.simpleName}: ${e.message}") }

                // ---- masterlist (item 7, two-bucket) + passive auth (unconditional, item 3 gate reads this) ----
                val ml = assets.open("DE_ML.ml").use { it.readBytes() }
                val root = assets.open("csca-germany-root.der").use { it.readBytes() }
                when (val result = MasterlistVerifier.load(ml, root)) {
                    is MasterlistVerifier.LoadResult.Failure -> {
                        masterlistReport = "master_list: INTEGRITY FAILURE (ok:false) — ${result.reason}"
                        passiveAuthVerdict = M0Probe.Verdict.unknown("master list integrity check failed: ${result.reason}")
                    }
                    is MasterlistVerifier.LoadResult.Success -> {
                        masterlistReport = "master_list: CMS-verified OK, declared=${result.masterList.certsDeclared} parsed=${result.masterList.certsParsed}"
                        trustedKeystoreCerts = result.masterList.keystore
                        passiveAuthVerdict = passiveAuthAgainst(result.masterList.keystore)
                    }
                }
                timeline.mark("passive_auth_verified")
            } catch (e: Exception) {
                Log.e(TAG, "M2 READ FAILED", e)
                return e
            }
            return null
        }

        private fun passiveAuthAgainst(keystore: java.security.KeyStore): M0Probe.Verdict {
            // M0Probe.passiveAuth only reads .keystore and .consistent from
            // MasterList; declared==parsed is already independently enforced
            // by MasterlistVerifier before it returns Success — this wrapper
            // exists only to reuse M0Probe.passiveAuth's signature-chain logic.
            val consistentWrapper = M0Probe.MasterList(certsDeclared = 1, certsParsed = 1, keystore = keystore)
            return M0Probe.passiveAuth(dg1File, sodFile, consistentWrapper)
        }

        override fun onPostExecute(result: Exception?) {
            mainLayout.visibility = View.VISIBLE
            loadingLayout.visibility = View.GONE
            if (result != null) {
                wipeSession(keepMrzAndMode = accessFailure)
                if (accessFailure) {
                    Snackbar.make(passportNumberView, R.string.error_read, Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(passportNumberView, result.toString(), Snackbar.LENGTH_LONG).show()
                }
                emitReport("verdict: FAIL\nfailure: ${result.javaClass.simpleName}: ${result.message}\n$masterlistReport")
                return
            }
            // Any completed read (success or a real masterlist "no") wipes the
            // session — only an access-establishment failure keeps it (F3).
            wipeSession(keepMrzAndMode = false)
            continueAfterRead(mode, passiveAuthVerdict!!, chipAuthField, accessProtocol, masterlistReport, dg1File)
        }
    }

    // --------------------------------------------------------- item 2/3 gate
    /** §6.2 item 3: mint (derive zktag / sign evidence) iff mode B AND
     * `passiveAuth.ok && passiveAuth.allowed == true`. Everything else
     * (mode A, or a masterlist real-no, or an integrity failure) shows the
     * verdict and stops — no biometric prompt, no zktag, no evidence. */
    private fun continueAfterRead(
        mode: PresentationMode,
        verdict: M0Probe.Verdict,
        chipAuthField: String,
        accessProtocol: String,
        masterlistReport: String,
        dg1File: DG1File,
    ) {
        val baseReport = buildString {
            append("mode: $mode\n")
            append("access_protocol: $accessProtocol\n")
            append("chip_auth (D21 payload field): $chipAuthField\n")
            append(masterlistReport).append("\n")
            append("passive_auth: $verdict\n")
        }

        // Single source of truth (MintGate) — see its doc for the root-cause
        // note on why the branch below now goes through emitReport.
        val mayMint = MintGate.mayMint(mode == PresentationMode.B, verdict)
        if (!mayMint) {
            emitReport(baseReport + "\nmint_gate: NOT MET — evidence: [] (D27${if (mode == PresentationMode.B) ", item 3: masterlist/passive-auth gate not satisfied" else ""})\nverdict: ${if (verdict.ok) "PASS (read)" else "FAIL (could not check)"}")
            return
        }

        // D38 (§6.2 item 1 amendment): a per-origin attester key needs an
        // origin to scope it to. A mode-B mint with no verified handoff
        // request has no verifier to bind to anyway — refused here, before
        // the biometric prompt ever shows, rather than falling back to a
        // demo/local "manual" key scope (owner-escalated choice, this
        // session: refuse-loudly over a `zkagent_attester_manual` alias).
        val origin = verifiedRequest?.origin
        if (mode == PresentationMode.B && origin == null) {
            Log.e(TAG, "M2 stage: mode B mint REFUSED — no verified request origin to scope the attester key to (D38)")
            emitReport(baseReport + "\nmint: REFUSED — mode B requires a verified request origin to scope the attester key to (D38); no handoff is pending")
            return
        }

        emitReport(baseReport + "\nmint_gate: MET — requesting biometric/device-credential authorization before minting (item 2)\n")
        Thread {
            // D38 amendment (2026-09-01 live-run finding, owner decision:
            // "isolate" — see DeviceKey class doc): the attester key alias
            // is now scoped to (origin, zktag), not origin alone — a
            // per-origin-only key let one verifier see two different
            // documents share a device key. The zktag needs no user auth to
            // DERIVE (unlike the key's USE, the signature, which stays
            // behind the biometric prompt below), so it is derived here,
            // before ensureKey/key generation — key generation itself also
            // needs no user auth.
            //
            // Same "one source, never re-derived a second way" discipline
            // as everywhere else in this file: handoff/verified are read
            // once here (and re-checked, in case the pending handoff was
            // cleared/replaced on the main thread in the interim — the same
            // defensive re-check mintAndMaybeHandoff always did), and the
            // resulting zktag/scopeDomain are threaded through
            // promptAndMint -> mintAndMaybeHandoff as parameters rather than
            // re-derived down there.
            val handoff = pendingHandoff
            val verified = verifiedRequest
            if (handoff == null || verified == null) {
                Log.e(TAG, "M2 stage: pending handoff / verifiedRequest disappeared before zktag derivation — refusing (D38)")
                runOnUiThread { emitReport(baseReport + "\nmint: FAILED — no verified request object to mint against (D38)") }
                return@Thread
            }
            // scope_domain comes from ONE source — the VERIFIED request's own
            // origin ([RequestTrust.originOf], D37) — not a fresh, unverified
            // re-parse of pendingHandoff.requestUri's host (items 13/14).
            val scopeDomain = runCatching { URI(verified.origin).host }
                .onFailure { e -> Log.w(TAG, "M2 stage: could not parse verified origin host: ${e.javaClass.simpleName}: ${e.message}") }
                .getOrNull()
            if (scopeDomain == null) {
                Log.e(TAG, "M2 stage: verified origin has no parseable host — refusing to mint")
                runOnUiThread { emitReport(baseReport + "\nmint: FAILED — verified origin has no parseable host") }
                return@Thread
            }
            val candidates = M0Probe.deriveCandidates(dg1File, null, null, domain = scopeDomain)
            val zktag = candidates["document_number"]
            if (zktag == null) {
                Log.w(TAG, "M2 stage: no document_number field to derive zktag from (D9)")
                runOnUiThread { emitReport(baseReport + "\nmint: FAILED — no document_number field to derive from (D9)") }
                return@Thread
            }
            // origin is guaranteed non-null here for mode B (checked above,
            // and a local val is stable across the lambda capture) — !! documents
            // that invariant rather than silently defaulting to an empty-string alias.
            val alias = DeviceKey.aliasForOriginAndZktag(origin!!, zktag)
            val keyState = try {
                DeviceKey.ensureKey(applicationContext, alias)
            } catch (e: Exception) {
                Log.e(TAG, "M2 stage: DeviceKey.ensureKey threw", e)
                runOnUiThread { emitReport(baseReport + "\nmint: FAILED — device key generation threw ${e.javaClass.simpleName}: ${e.message}") }
                return@Thread
            }
            runOnUiThread { promptAndMint(keyState, baseReport, zktag, scopeDomain) }
        }.start()
    }

    private fun promptAndMint(keyState: DeviceKey.KeyState, baseReport: String, zktag: String, scopeDomain: String) {
        val sig = DeviceKey.initSignature(keyState)
        if (sig == null) {
            Log.w(TAG, "M2 stage: DeviceKey.initSignature returned null — no usable device key/signature")
            emitReport(baseReport + "\nmint: FAILED — no usable device key/signature (see algorithm matrix in logcat)")
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorizedSig = result.cryptoObject?.signature ?: sig
                    // 2026-09-01 bug fix: mintAndMaybeHandoff does network I/O
                    // (handoff fetch + direct_post) and this callback runs on
                    // the main thread — NetworkOnMainThreadException. Move it
                    // to a background thread, matching the Thread{}.start()
                    // idiom used elsewhere in this file (runMasterlistProbe,
                    // the DeviceKey.ensureKey call above). Safe to defer: the
                    // key's biometric auth is crypto-object-bound (per-use,
                    // validity duration 0 — see DeviceKey.specBuilder /
                    // authModeLabel), not time-window-bound, so using
                    // [authorizedSig] later on another thread does not risk
                    // the auth window expiring the way a validity-duration
                    // key would.
                    Thread { mintAndMaybeHandoff(keyState, authorizedSig, baseReport, zktag, scopeDomain) }.start()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    emitReport(baseReport + "\nmint: REFUSED — biometric/device-credential error $errorCode: $errString\nverdict: PASS (read only, no mint)")
                }

                override fun onAuthenticationFailed() {
                    Log.i(TAG, "M2 stage: biometric match failed once, prompt remains open")
                }
            },
        )
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(sig))
    }

    /** Signs the item-9 evidence message (over the `zktag`/`scopeDomain`
     * [continueAfterRead]'s Thread already derived, BEFORE key generation —
     * D38 amendment, see [DeviceKey] class doc — and threaded through
     * [promptAndMint]) with the ALREADY-AUTHORIZED device key, and — if a
     * [pendingHandoff] is queued — POSTs `direct_post`. zktag is never
     * fully rendered on screen; only a truncated hash, matching this
     * project's value-free logging discipline elsewhere.
     *
     * **Runs on a background thread** (see [promptAndMint]'s
     * `onAuthenticationSucceeded`) — `HandoffClient.fetchRequest` and
     * `postDirectPost` are blocking network calls. Every [emitReport] call
     * below is therefore wrapped in [runOnUiThread], and [pendingHandoff] is
     * cleared on the main thread too, once the handoff has definitively
     * completed or failed — never mid-flight.
     *
     * @param zktag the SAME value [continueAfterRead]'s Thread derived to
     *   build [keyState]'s (origin, zktag)-scoped alias — never re-derived
     *   here, so there is exactly one derivation per mint, not two that
     *   could drift apart.
     * @param scopeDomain likewise, the same verified-origin host already
     *   used to derive [keyState]'s alias. */
    private fun mintAndMaybeHandoff(keyState: DeviceKey.KeyState, signature: Signature, baseReport: String, zktag: String, scopeDomain: String) {
        // D38: a mode-B mint always has a pending, VERIFIED handoff by the
        // time it reaches here — continueAfterRead's D38 guard refuses
        // before this function is ever entered otherwise (no verified
        // origin -> no key scope -> refused before the biometric prompt).
        // Guarded again anyway rather than ever mint against an absent or
        // unverified request object.
        val handoff = pendingHandoff
        val verified = verifiedRequest
        if (handoff == null || verified == null) {
            Log.e(TAG, "M2 stage: reached mint with no pending handoff / verifiedRequest — refusing to proceed (D38)")
            runOnUiThread { emitReport(baseReport + "\nmint: local signature OK, but handoff: REFUSED — no verified request object to mint against (D38)") }
            return
        }
        val threshold = 18
        val claim = mapOf("over_threshold" to true, "threshold" to threshold)
        // §6.2 items 13/14: reuse the request object already fetched and
        // ES256-verified at capture time ([beginHandoffVerification]) —
        // never re-fetch here. Same nonce, same verified fields, one fetch
        // per handoff.
        val requestObject: JSONObject = verified.json
        Log.i(TAG, "M2 stage: using pre-verified handoff request object — origin=${verified.origin} signature_verified=true (no re-fetch)")
        // OpenID4VP request-object JSON, TOP LEVEL — response_uri/state/
        // client_id/response_mode live here (server.mjs ~line 230-270), NOT
        // inside zkagent.challenge (which carries only nonce/tier/expiry).
        // 2026-09-01 bug: this file previously read response_uri/state off
        // `challenge`, so a live verifier's request object always looked
        // like it "carried no response_uri" even though it was present one
        // level up.
        val zkagent = verified.json.optJSONObject("zkagent") ?: verified.json
        val challenge = zkagent.optJSONObject("challenge") ?: JSONObject()
        val nonce = challenge.optString("nonce", "")
        Log.i(TAG, "M2 stage: handoff challenge parsed — nonce_present=${nonce.isNotEmpty()} response_uri_present=${requestObject.has("response_uri")}")

        // D38: pubDer is read at keyState.alias — the SAME per-origin key
        // ensureKey/initSignature just used — never a second-guessed alias.
        val pubDer = DeviceKey.currentPublicKeyDer(keyState.alias)
        if (pubDer == null) {
            Log.e(TAG, "M2 stage: could not read the public key bytes for alias — refusing to mint (D38 evidence requires pubkey)")
            runOnUiThread { emitReport(baseReport + "\nmint: FAILED — could not read the device key's public key bytes (D38 evidence requires pubkey)") }
            return
        }
        val message = EvidenceSigner.messageFor(keyState.algorithm, claim, nonce, scopeDomain, zktag)
        val evidence = EvidenceSigner.sign(signature, message, keyState.algorithm, pubDer)

        val zktagHashPrefix = java.security.MessageDigest.getInstance("SHA-256").digest(zktag.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

        // Task 2 (owner-approved scope): assert what the KEYSTORE actually
        // holds and what plug was actually chosen — never what was merely
        // requested — per "probe must assert what came back" (F2/KEY TEST:
        // this device silently substitutes P-256 for Ed25519).
        val keyDetails = DeviceKey.currentKeyDetails(keyState.alias)
        val deviceKeyLine = if (keyDetails != null) {
            "device_key: alg=${keyDetails.jcaAlgorithm} curve=${keyDetails.curve} security_level=${keyDetails.securityLevel} " +
                "origin=${keyDetails.origin} user_auth_required=${keyDetails.userAuthRequired} auth_validity=${keyDetails.authValiditySeconds}s\n"
        } else {
            Log.w(TAG, "M2 stage: DeviceKey.currentKeyDetails() failed — could not assert stored key facts")
            "device_key: FAILED to assert stored key facts (KeyInfo query failed)\n"
        }
        val deviceKeyOriginLine = "device_key: ${if (keyState.reusedExistingKey) "reused existing alias" else "created this mint"}\n"
        // D38 item 2: which VERIFIED REQUEST origin this per-origin key is
        // scoped to (not to be confused with keyDetails.origin above, which
        // is KeyInfo's GENERATED/IMPORTED provenance field — different
        // "origin"). Already shown at consent time (handoffStatus); this is
        // the same value-free string, now also in the mint report.
        val keyScopeLine = "key_scope: ${verified.origin}\n"
        val evidencePlug = "${evidence.type}/${evidence.version}"
        // "requested=" was misleading once D31's any-of evidence_required
        // shipped: a verifier's evidence_required lists ACCEPTED
        // alternatives, it never singles out sig-ed25519/1 as "requested" —
        // DeviceKey.PREFERRED_EVIDENCE_TYPE is this DEVICE's own preference
        // order (D36), not anything read off the request. Labelled
        // accordingly; see evidenceRequiredLine below for what the verifier
        // actually said it accepts.
        val evidencePlugLine = if (evidencePlug == DeviceKey.PREFERRED_EVIDENCE_TYPE) {
            "evidence_plug: $evidencePlug\n"
        } else {
            "evidence_plug: device_preference=${DeviceKey.PREFERRED_EVIDENCE_TYPE} used=$evidencePlug reason=not selected by DeviceKey's preference order on this device — see device_key_tradeoff\n"
        }
        // Log-only parse of the request object's evidence_required shape
        // (D31 any-of groups) — value-free, and NOT a behavioural input to
        // which plug the device offers (D36: device preference order alone
        // decides that). See RequestTrust.describeEvidenceRequired's doc.
        val evidenceRequiredLine = "evidence_required: ${RequestTrust.describeEvidenceRequired(requestObject)}\n"

        var report = baseReport +
            "\nmint: OK\n" +
            deviceKeyLine +
            deviceKeyOriginLine +
            keyScopeLine +
            evidencePlugLine +
            evidenceRequiredLine +
            "device_key_algorithm: ${keyState.algorithm} (${keyState.securityLevel}, sig_alg=${keyState.signatureAlgorithm})\n" +
            "device_key_tradeoff: ${keyState.tradeoffNote}\n" +
            "evidence_type: ${evidence.type}/${evidence.version} key_id=${evidence.keyId}\n" +
            "zktag_sha256_prefix (value-free, never the raw zktag): $zktagHashPrefix\n" +
            "scope_domain: $scopeDomain\n"

        // D38: handoff is guaranteed non-null here (guarded above) — mode B
        // always has a pending handoff to answer now, so this is no longer
        // conditional the way it was pre-D38.
        try {
            val presentation = HandoffClient.buildPresentation("B", claim, challenge, zktag, listOf(evidence))
            val responseUri = requestObject.optString("response_uri", "").ifEmpty { null }
            if (responseUri == null) {
                Log.w(TAG, "M2 stage: handoff request object carries no top-level response_uri — cannot direct_post")
                report += "handoff: FAILED — request object carries no response_uri\n"
            } else {
                val state = if (requestObject.has("state")) requestObject.getString("state") else null
                Log.i(TAG, "M2 stage: handoff direct_post -> $responseUri (state_present=${state != null})")
                val result = HandoffClient.postDirectPost(responseUri, state, presentation)
                if (result.httpStatus !in 200..299) {
                    Log.w(TAG, "M2 stage: handoff direct_post response NON-2xx http_status=${result.httpStatus} body=${result.body}")
                } else {
                    Log.i(TAG, "M2 stage: handoff direct_post response http_status=${result.httpStatus} body=${result.body}")
                }
                report += "handoff: direct_post http_status=${result.httpStatus} -> ${result.body}\n"
            }
        } catch (e: Exception) {
            Log.e(TAG, "M2 stage: handoff direct_post FAILED", e)
            report += "handoff: FAILED ${e.javaClass.simpleName}: ${e.message}\n"
        }
        // Handoff has now definitively completed or failed — clear on the
        // main thread (item 6 lifecycle discipline). verifiedRequest is
        // cleared in lockstep — it has no meaning without a pendingHandoff.
        runOnUiThread {
            pendingHandoff = null
            verifiedRequest = null
            setModeGroupEnabled(true)
        }
        report += "\nverdict: PASS (minted)"
        runOnUiThread { emitReport(report) }
    }

    // -------------------------------------------------------- masterlist UI
    private fun runMasterlistProbe() {
        val log = StringBuilder("\n===== MASTERLIST PROBE =====\n")
        try {
            val ml = assets.open("DE_ML.ml").use { it.readBytes() }
            val root = assets.open("csca-germany-root.der").use { it.readBytes() }
            val t0 = System.nanoTime()
            val result = MasterlistVerifier.load(ml, root)
            val ms = (System.nanoTime() - t0) / 1_000_000
            when (result) {
                is MasterlistVerifier.LoadResult.Failure -> log.append("full_load: INTEGRITY FAILURE (ok:false) — ${result.reason} (${ms}ms)\n")
                is MasterlistVerifier.LoadResult.Success -> log.append("full_load: OK declared=${result.masterList.certsDeclared} parsed=${result.masterList.certsParsed} (${ms}ms)\n")
            }
            // NEGATIVE: half-loaded CMS file — must be an integrity failure (ok:false), never a pass.
            val half = ml.copyOf(ml.size / 2)
            val halfResult = MasterlistVerifier.load(half, root)
            log.append(
                "NEGATIVE half_loaded: " + when (halfResult) {
                    is MasterlistVerifier.LoadResult.Failure -> "REFUSED (ok:false) — ${halfResult.reason} (good)"
                    is MasterlistVerifier.LoadResult.Success -> "DID NOT REFUSE — INVALID RUN, this must never happen"
                } + "\n",
            )
        } catch (e: Exception) {
            Log.e(TAG, "M2 stage: masterlist probe threw", e)
            log.append("PROBE FAILED: ${e.javaClass.simpleName}: ${e.message}\n")
        }
        log.append("===== END =====")
        runOnUiThread { emitReport(log.toString()) }
    }

    /** Debug-only, zero-tap: exercises [DeviceKey.ensureKey]'s generate ->
     * reuse path TWICE in this one process, without an NFC session — the
     * key path is independent of the chip (see class doc / DeviceKey.kt F8).
     * This is the hypothesis test for "keystore key REUSE differs from the
     * GENERATE path" — if it holds, this button reproduces it without a
     * document. */
    private fun runDeviceKeyProbe() {
        val log = StringBuilder("\n===== DEVICE KEY SELF-TEST =====\n")
        try {
            val t0 = System.nanoTime()
            val first = DeviceKey.ensureKey(applicationContext, DeviceKey.PROBE_ALIAS) // D38: self-test targets PROBE_ALIAS, never a per-origin mint alias
            val ms0 = (System.nanoTime() - t0) / 1_000_000
            log.append("call_1 (generate-or-reuse): algorithm=${first.algorithm} level=${first.securityLevel} auth_mode=${first.authMode} winner_row=${first.winnerRowId} (${ms0}ms)\n")
            for (a in first.matrix) log.append("  matrix ${a.rowId}: ${if (a.ok) "OK level=${a.actualSecurityLevel}" else "FAILED ${a.exception}"}\n")

            val sig1 = DeviceKey.initSignature(first)
            log.append("call_1 initSignature: ${if (sig1 != null) "OK (non-null Signature)" else "FAILED (null)"}\n")

            val t1 = System.nanoTime()
            val second = DeviceKey.ensureKey(applicationContext, DeviceKey.PROBE_ALIAS)
            val ms1 = (System.nanoTime() - t1) / 1_000_000
            log.append("call_2 (must be REUSE, same process): algorithm=${second.algorithm} level=${second.securityLevel} auth_mode=${second.authMode} winner_row=${second.winnerRowId} matrix_size=${second.matrix.size} (${ms1}ms)\n")
            log.append("call_2 reuse check: ${if (second.winnerRowId == null && second.matrix.isEmpty()) "OK (reuse path, no re-probe)" else "UNEXPECTED (re-ran the generate matrix on a call that should have reused)"}\n")

            val sig2 = DeviceKey.initSignature(second)
            log.append("call_2 initSignature: ${if (sig2 != null) "OK (non-null Signature)" else "FAILED (null)"}\n")
            log.append("auth_mode consistency: call_1=${first.authMode} call_2=${second.authMode} ${if (first.authMode == second.authMode) "(match)" else "MISMATCH — possible F8 self-heal fired between calls"}\n")
        } catch (e: Throwable) {
            Log.e(TAG, "M2 stage: device key probe threw", e)
            log.append("PROBE FAILED: ${e.javaClass.simpleName}: ${e.message}\n")
        }
        log.append("===== END =====")
        runOnUiThread { emitReport(log.toString()) }
    }

    private fun convertDate(input: String?): String? {
        if (input == null) return null
        return try {
            SimpleDateFormat("yyMMdd", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input)!!)
        } catch (e: ParseException) {
            null
        }
    }

    private fun loadDate(editText: EditText): Calendar {
        val calendar = Calendar.getInstance()
        if (editText.text.isNotEmpty()) {
            try {
                calendar.timeInMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(editText.text.toString())!!.time
            } catch (e: ParseException) {
                Log.w(TAG, e)
            }
        }
        return calendar
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val STATE_LAST_REPORT = "m2_last_report"
    }
}
