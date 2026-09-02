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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
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
import java.security.SecureRandom
import java.security.Signature
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * zkagent M2 reference scanner — PRD `docs/product/zkagent-prd.md` §6.2,
 * items 1-11. A REWRITE, not a graduated spike: drawn from
 * `spikes/m2-scan` (masterlist/passive-auth read path) and
 * `spikes/m2-session-poc` (StrongBox key + biometric composition, item 12's
 * already-PASSED POC), but restructured where the PRD requires it —
 * see the per-item notes below and the conformance report.
 *
 * ---------------------------------------------------------------------
 * §6.2 item 4 (F5, the mode-radio bug) — ELIMINATED BY CONSTRUCTION
 * (2026-09 real-device change, "remove the mode radio entirely"):
 * ---------------------------------------------------------------------
 * F5 (`docs/logs/M2-SCAN-EVIDENCE.md`: the mode radio displayed "B" once
 * while a scan actually ran in mode A) was never root-caused in
 * `spikes/m2-scan`'s `MainActivity.kt`. The first rewrite of this file
 * mitigated it structurally (read the radio exactly once, disable it
 * immediately) rather than assuming a rewrite fixes an unreproduced bug by
 * construction; that mitigation held (F5 was closed 2026-09-01, not
 * reproduced). The owner's next call went further: mode is not a user
 * choice at all. There is no RadioGroup/RadioButton anywhere in this file
 * any more — [lockedMode] is DERIVED, never read from a UI control: a
 * verified handoff's `zkagent.tier` sets it (D33/D34, via
 * [tierOutcomeFor]); a bare local scan with no verified handoff is mode A
 * BY DEFINITION (owner: "a bare local scan with no verified request is
 * mode A by definition"). [lockModeAndArm] remains item 4's ONE call site
 * that writes [lockedMode], but it has nothing left to READ from a
 * control — a UI/session-state mismatch of F5's shape is now impossible
 * for the strongest possible reason: the surface that could disagree with
 * the executed mode does not exist. [modeStatusView] is a plain, non-
 * interactive TextView showing the DERIVED mode (see [refreshModeStatus]);
 * it is a display, never an input.
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
 * lets [lockModeAndArm] (item 4's ONE call site for [lockedMode]) derive and
 * lock the mode from the request's `zkagent.tier` — [lockedMode] still has
 * exactly one writer, it is just fed from [verifiedRequest] when a handoff
 * is pending, or defaults to mode A when it is not (2026-09: there is no
 * mode radio to fall back to reading any more — see the item 4 section
 * above). [applyHandoffVerificationOutcome] calls [refreshModeStatus] on a
 * successful verify, so the DERIVED mode is visible (D33: the app "sets"
 * the mode) the instant verification succeeds, not only once Lock is
 * pressed — this is a DISPLAY update, not a read of anything: there is no
 * control left to read. A tier this preview can't map (C, absent, invalid)
 * shows a neutral "pending" status instead of guessing — the actual
 * refusal is reported once the user tries to lock (item 13's fail-loud path,
 * UNCHANGED by the radio's removal: it now guards the derivation instead of
 * the preselect). [mintAndMaybeHandoff] later REUSES [verifiedRequest]
 * rather than re-fetching — same nonce, same verified fields, one fetch per
 * handoff. Any verification failure (origin mismatch, bad/missing/wrong-alg
 * signature, no resolvable key) is a refusal: [pendingHandoff] and
 * [verifiedRequest] are cleared, the derived mode status reverts to its
 * bare-scan default (no handoff is pending any more), and the refusal is
 * logged AND reported — never a silent downgrade to trusting the fields
 * anyway.
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

    // ---- §6.2 item 16 (D44): per-scan report log. D58 step 1: this class
    // is now also the sole owner of the last-rendered-report text (formerly
    // a separate `lastReportText` field here, written at two independent
    // sites — see [ReportLog.lastText]'s doc for why that was finding #7).
    // An ADDITIONAL CONSUMER of [emitReport]'s single write path — see that
    // function's doc. In-memory only; restored/saved across Activity
    // recreation via [restoreReport]/`onSaveInstanceState` (D35), cleared
    // inside [wipeSession]'s `!keepMrzAndMode` branch alongside everything
    // else that branch resets. ----
    private val reportLog = ReportLog()

    // ---- D55: whether a chip read is currently in flight — the ONLY input
    // to [showPane] besides the tab selection itself. Set ONLY by
    // [startSession] (true) and as the FIRST statement of
    // [ReadTask.onPostExecute] (false, on every exit path) — see
    // [showPane]'s doc. ----
    private var readInProgress: Boolean = false

    // ---- D56: salted hash of the previous read attempt's MRZ fields, held
    // ONLY in memory — never rendered, never logged, never persisted. See
    // [MrzChangeTracker]'s class doc for why it is salted. Reset to null
    // wherever the MRZ itself is cleared ([wipeSession]'s `!keepMrzAndMode`
    // branch), so the next attempt correctly reads as a first attempt. ----
    private var lastMrzHash: String? = null

    private lateinit var passportNumberView: EditText
    private lateinit var expirationDateView: EditText
    private lateinit var birthDateView: EditText
    // 2026-09 real-device fix: no RadioGroup/RadioButton any more — mode
    // is DERIVED (see class doc, item 4 section) and this TextView is its
    // ONLY display, doubling as the pre-lock "current derived mode"
    // indicator and the post-lock "Locked: mode X" banner (see
    // refreshModeStatus / lockModeAndArm).
    private lateinit var modeStatusView: TextView
    private lateinit var lockButton: Button
    private lateinit var tabLayout: TabLayout
    private lateinit var mainLayout: View
    private lateinit var logLayout: View
    private lateinit var loadingLayout: View
    private lateinit var reportView: TextView
    private lateinit var logView: TextView
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
        modeStatusView = findViewById(R.id.mode_status)
        lockButton = findViewById(R.id.button_lock_and_scan)
        tabLayout = findViewById(R.id.tab_layout)
        mainLayout = findViewById(R.id.main_layout)
        logLayout = findViewById(R.id.log_layout)
        loadingLayout = findViewById(R.id.loading_layout)
        reportView = findViewById(R.id.report_view)
        logView = findViewById(R.id.log_view)
        handoffStatus = findViewById(R.id.handoff_status)
        handoffManualInput = findViewById(R.id.handoff_manual_input)

        // §6.2 item 16 (D44) / D55: tab selection only changes which pane
        // [showPane] computes — see that function's doc for the single-
        // write-site invariant this listener now defers to entirely.
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showPane() }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            // D55: was a no-op — part of why a user stranded on the Log
            // tab after a failed read had no way back. Now idempotent:
            // re-tapping the current tab re-applies [showPane]'s decision.
            override fun onTabReselected(tab: TabLayout.Tab) { showPane() }
        })

        // No SharedPreferences/DataStore read or write anywhere in this
        // activity — MRZ fields start empty every launch (NO-GO #9).

        // D58 step 1 (finding #7): restores the report/log cluster across
        // an Activity re-creation (config change / background memory
        // reclaim) — an in-memory Bundle via onSaveInstanceState, never
        // disk. wipeSession() never touches reportView, so within one
        // Activity instance the report already survived onStop; this
        // additionally survives the instance itself being destroyed and
        // recreated, which is what actually looked like "onStop wiped the
        // report" from the UI. See [restoreReport]'s doc — this used to be
        // two direct view-write sites here, bypassing [emitReport] entirely
        // (finding #7's exact defect); now a NAMED sibling of [emitReport]
        // on the same owner (ReportLog), the only other writer of this
        // cluster's state.
        restoreReport(savedInstanceState)

        // D55: tab state and the restored log are both in place above —
        // one call so a recreated Activity is never left in a stale
        // both-visible (or wrong-pane) combination. See [showPane]'s doc.
        showPane()

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

        refreshModeStatus()
        handleIncomingIntent(intent)
    }

    /** §6.2 item 4 (2026-09 real-device fix, "remove the mode radio
     * entirely" — see class doc): the ONE place [modeStatusView]'s text is
     * derived from current state, whenever that state changes in a way
     * that could change what it should show — a captured-but-unverified
     * handoff, a verified handoff (mapped tier or "pending" for one this
     * build can't map), or no handoff at all (mode A by definition, the
     * owner's own words). NEVER called while [lockedMode] is set — once
     * locked, [lockModeAndArm] owns the text directly ("Locked: mode X —
     * tap your document now"), and [wipeSession] hands control back here
     * only once [lockedMode] is cleared. */
    private fun refreshModeStatus() {
        val verified = verifiedRequest
        modeStatusView.text = when {
            verified != null -> when (RequestTrust.tierOf(verified.json)) {
                "A" -> "Mode: A — anonymous"
                "B" -> "Mode: B — recognisable to this site"
                else -> "Mode: pending — tap Lock & scan to see the outcome"
            }
            pendingHandoff != null -> "Mode: verifying the site's request…"
            else -> "Mode: A — anonymous (no site request pending)"
        }
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
    /** The ONE call site in this file that writes [lockedMode]. §6.2 item 13
     * (D33): when a handoff is pending, it comes from the verified request's
     * `zkagent.tier`; otherwise (2026-09: no mode radio left to read — see
     * class doc) it defaults to mode A by definition. Arms NFC dispatch once
     * derived — never a second call site. */
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
            // 2026-09-01 real-device fix ("fix 3" — a spent/aged handoff
            // session inviting a document tap that cannot succeed): the
            // EARLIEST possible check, before the user is even asked to tap
            // — see SESSION_EXPIRED_MESSAGE's doc and the belt-and-
            // suspenders re-check in [continueAfterRead]. A "consumed"
            // (already-answered) session cannot reach here at all: every
            // definitive mint outcome clears [pendingHandoff]/
            // [verifiedRequest] (see `mintAndMaybeHandoff`'s doc), so the
            // only remaining "no usable session" condition this app can
            // detect locally is EXPIRY — read straight off the challenge
            // this request object already carries, never a network call.
            val expiresAt = RequestTrust.expiresAtOf(verified.json)
            if (expiresAt != null && RequestTrust.isExpired(expiresAt, System.currentTimeMillis())) {
                Log.e(TAG, "M2 stage: handoff REFUSED — verification session expired before lock (expires_at=$expiresAt)")
                emitReport(
                    "handoff: REFUSED — verification session expired before lock (expires_at=$expiresAt)",
                    ReportLog.DisclosureSummary(
                        site = siteTitleFor(verified.origin),
                        result = "Refused — verification session expired",
                        sent = "nothing left this device",
                        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                    ),
                )
                showBlockingOutcomeDialog(SESSION_EXPIRED_MESSAGE, isAccessEstablishmentFailure = false)
                return
            }
            when (val outcome = tierOutcomeFor(verified)) {
                is TierOutcome.Ok -> mode = outcome.mode
                is TierOutcome.Unsupported -> {
                    Log.e(TAG, "M2 stage: pending handoff requests tier C — not supported in this build (item 13)")
                    val reason = "handoff: REFUSED — tier C requested, not supported in this build (no tier-C flow)"
                    emitReport(
                        reason,
                        ReportLog.DisclosureSummary(
                            site = siteTitleFor(verified.origin),
                            result = "Refused — the site asked for a verification tier this app does not support",
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                    )
                    showBlockingOutcomeDialog(reason, isAccessEstablishmentFailure = false)
                    return
                }
                is TierOutcome.Invalid -> {
                    Log.e(TAG, "M2 stage: pending handoff request has absent/invalid tier (got: ${outcome.got}) — refusing, no default mode (item 13)")
                    val reason = "handoff: REFUSED — request tier absent or invalid (got: ${outcome.got}), no default mode"
                    emitReport(
                        reason,
                        ReportLog.DisclosureSummary(
                            site = siteTitleFor(verified.origin),
                            result = "Refused — the site's request did not specify a valid verification mode",
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                    )
                    showBlockingOutcomeDialog(reason, isAccessEstablishmentFailure = false)
                    return
                }
            }
        } else {
            // §6.2 item 4 (2026-09 real-device change, "remove the mode
            // radio entirely"): no control to read any more — a bare local
            // scan with no verified handoff is mode A BY DEFINITION (owner:
            // "a bare local scan with no verified request is mode A by
            // definition"). Mode B is reachable ONLY via a verified
            // handoff's tier, above.
            mode = PresentationMode.A
        }

        lockedMode = mode
        lockButton.isEnabled = false
        modeStatusView.text = "Locked: mode ${lockedMode} — tap your document now"
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
        // D58 step 1: reads FROM the owner (reportLog.lastText), not a
        // parallel MainActivity field — see ReportLog.lastText's doc.
        reportLog.lastText?.let { outState.putString(STATE_LAST_REPORT, it) }
        outState.putStringArrayList(STATE_LOG_ENTRIES, ArrayList(reportLog.entriesSnapshot()))
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
                // Finding #10 (.claude/remember/findings.md) MITIGATION: an
                // av:// intent arriving while a session is locked, or while
                // a chip read is literally in flight, previously overwrote
                // pendingHandoff/verifiedRequest with no guard at all — see
                // beginHandoffVerification's unconditional writes and
                // HandoffAdmission's class doc for the full finding and why
                // this is a MITIGATION, not the ownership fix (that is the
                // lock-time SessionState snapshot in the refactor, not this
                // change).
                //
                // MUST NOT use showBlockingOutcomeDialog here (2026-09-02
                // coordinator correction, verified at source): that dialog's
                // OK handler (see its doc / :905-909) is a TERMINAL-OUTCOME
                // state transition — it nulls pendingHandoff/verifiedRequest
                // and calls wipeSession(false), clearing lockedMode and the
                // MRZ fields. A refused foreign intent is not a terminal
                // outcome of the LOCKED SESSION; routing it through that
                // dialog would let the user's own OK tap destroy the
                // legitimate in-progress session the guard exists to
                // protect — a one-tap DoS, and the exact two fields (finding
                // #10) this mitigation exists to leave untouched. A
                // non-blocking Snackbar (already this file's mechanism for a
                // non-terminal notice, e.g. the QR-capture-cancelled/no-QR-
                // found Snackbars above) causes no state transition at all.
                //
                // Deliberately no emitReport/log entry on refusal (owner
                // ruling, findings.md #13): a refused foreign intent must
                // not be able to append to user-visible persisted state
                // (ReportLog.entries is unbounded and persisted whole into
                // the Bundle) — the Snackbar and the logcat line below are
                // the only outputs.
                if (!HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = lockedMode != null, readInProgress = readInProgress)) {
                    Log.e(TAG, "M2 stage: av:// handoff REFUSED — session locked or read in progress (D57 mitigation for finding #10)")
                    Snackbar.make(reportView, HANDOFF_REFUSED_MID_SESSION_MESSAGE, Snackbar.LENGTH_LONG).show()
                    return
                }
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
                // D56: value-free diagnostic — did the MRZ details actually
                // CHANGE since the previous attempt in this process? See
                // [MrzChangeTracker]'s class doc: never the field values
                // themselves, only the comparison verdict, the document
                // number's LENGTH, and whether each date parsed (both
                // always true here — the guard above already required it —
                // named explicitly because [MrzChangeTracker] is a general
                // helper, not because either can be false at this call
                // site today).
                val currentMrzHash = MrzChangeTracker.hash(passportRaw, birthRaw, expirationRaw, mrzHashSalt)
                Log.i(TAG, MrzChangeTracker.logLine(
                    MrzChangeTracker.compare(lastMrzHash, currentMrzHash, docLen = passportRaw.length, dobOk = true, expOk = true)
                ))
                lastMrzHash = currentMrzHash
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
     * The Lock button is held off until this resolves, so there is no
     * window where the user could lock a session against an
     * unverified/wrong tier (2026-09: there is no mode radio left to hold
     * off — [refreshModeStatus] shows "verifying…" instead). */
    private fun beginHandoffVerification(handoff: HandoffClient.PendingHandoff) {
        pendingHandoff = handoff
        verifiedRequest = null
        handoffStatus.text = "Handoff request received — verifying signature and origin…"
        refreshModeStatus()
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
                // verification succeeds (2026-09: via refreshModeStatus — there
                // is no control left to write to, only a display). C/absent/
                // invalid tiers show a neutral "pending" status rather than
                // guessing; the fail-loud refusal happens when the user tries
                // to lock. lockModeAndArm() remains the only place that turns
                // this into lockedMode.
                refreshModeStatus()
                lockButton.isEnabled = true
            }
            is RequestTrust.Outcome.Refused -> {
                Log.e(TAG, "M2 stage: handoff REFUSED — ${outcome.reason}")
                // §6.2 item 16 (D46): the handoff's origin was never
                // VERIFIED (that is exactly what failed here), so the entry
                // title is the fixed no-site label, not the unconfirmed
                // origin the failed request claimed — an unverified origin
                // is never shown as though it were trusted (siteTitleFor's
                // doc / D37/D42).
                emitReport(
                    "handoff: REFUSED — ${outcome.reason}",
                    ReportLog.DisclosureSummary(
                        site = SITE_NO_HANDOFF,
                        result = "Refused — the site's request could not be verified",
                        sent = "nothing left this device",
                        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                    ),
                )
                handoffStatus.text = "Handoff refused (${outcome.reason}) — you may still scan manually."
                // §6.2 item 15 (D43): the state transition (clearing
                // pendingHandoff/verifiedRequest, reverting the derived mode
                // display via wipeSession) happens on dialog dismissal, not
                // immediately — see showBlockingOutcomeDialog's doc.
                showBlockingOutcomeDialog("Handoff refused: ${outcome.reason}", isAccessEstablishmentFailure = false)
            }
        }
    }

    /** §6.2 item 6: MRZ + [lockedMode] are kept on an access-establishment
     * failure OR (2026-09) a transient chip-communication failure — see
     * [FailureTransition]'s three-bucket doc. Every other case (success, or
     * a later-stage failure) wipes both. §6.2 item 13: if a VERIFIED
     * handoff is still pending across a retry, the derived mode display
     * stays as it was for that handoff — a failed read must not silently
     * revert to the bare-scan default out from under a still-pending
     * handoff (2026-09: [refreshModeStatus] already handles this
     * correctly, since it re-derives from [pendingHandoff]/
     * [verifiedRequest], not from a separately-tracked enabled/disabled
     * flag the way the removed mode radio needed). */
    private fun wipeSession(keepMrzAndMode: Boolean) {
        if (!keepMrzAndMode) {
            passportNumberView.text?.clear()
            expirationDateView.text?.clear()
            birthDateView.text?.clear()
            lockedMode = null
            lockButton.isEnabled = true
            refreshModeStatus()
            // D56: the MRZ itself is cleared above — the next attempt must
            // correctly read as a first attempt, not "unchanged" against a
            // hash of details that no longer exist on screen.
            lastMrzHash = null
            // §6.2 item 16 (D45): the log's lifetime is DECOUPLED from this
            // branch — a per-scan session wipe, successful or not, MUST NOT
            // clear it. See ReportLog's class doc for why (D44's literal
            // clear-on-wipe rule self-contradicted "successive scans
            // accumulate": this call fires on every completed read,
            // including a successful one, so the log never held more than
            // one entry). The log now empties only when the app process
            // does.
        }
    }

    // ---------------------------------------------------------------------
    /** §6.2 items 5/6 — the ONE place a NEW report is ever rendered to
     * [reportView] (its sibling, [restoreReport], is the ONLY other writer
     * of this cluster's state — see that function's doc; together they are
     * now the true "ONE place per event category" this KDoc originally
     * claimed alone, closing finding #7). Every terminal outcome (success,
     * failure, refusal, gate-not-met, exception) and every intermediate
     * progress state MUST go through this function and ONLY this function
     * — see [MintGate]'s doc for why: the 2026-08-31 stall (runs 2/3
     * producing an on-screen verdict with ZERO logcat trace) was exactly a
     * `reportView.text = ...` site that never called `Log.i`. Value-free by
     * construction — callers pass only verdict booleans, step names,
     * counts, hashes, algorithm names, timings; never MRZ/DG1 field values
     * or the raw zktag.
     *
     * D58 step 1: [reportLog] (specifically [ReportLog.append]) is now the
     * SINGLE owner of this cluster's state (`lastText`, `entries`) — this
     * function writes the two real Android views ([reportView], [logView])
     * from what the owner returns, and is otherwise a thin renderer; it no
     * longer holds a parallel `lastReportText` field of its own. */
    /** @param attemptId identifies a scan/mint attempt so its eventual
     *   terminal outcome can REPLACE its own "In progress" entry — see
     *   [ReportLog.append]'s doc (2026-09-01 real-device fix). Null for
     *   every call that is not part of a pending/terminal pair.
     * @param pending true ONLY for the one "In progress" report a mint
     *   attempt emits while awaiting biometric authorization. */
    private fun emitReport(text: String, summary: ReportLog.DisclosureSummary, attemptId: String? = null, pending: Boolean = false) {
        // §6.2 item 16 (D46): the log tab is an ADDITIONAL CONSUMER of this
        // one write site — never a second write site. [text] is exactly
        // what reportView shows, unmodified; [summary] is the value-free,
        // plain-language disclosure summary ReportLog titles and leads the
        // entry with — see ReportLog's class doc. Extending THIS call site
        // (rather than adding a second one) is the mechanism D46 itself
        // requires — [attemptId]/[pending] are the same discipline applied
        // to the 2026-09-01 stale-in-progress-entry fix.
        reportLog.append(text, summary, attemptId = attemptId, pending = pending)
        reportView.text = reportLog.lastText
        logView.text = reportLog.rendered(titleSizePx = logTitleSizePx())
        Log.i(TAG, "\n===== M2 REPORT (value-free) =====\n$text\n===== END =====")
    }

    /** §6.2 item 16 (D44/D35) — D58 step 1: the NAMED sibling of
     * [emitReport] on the SAME owner ([reportLog]), for the one other event
     * category this cluster's state has — restoring across Activity
     * recreation, from `onCreate`'s `savedInstanceState`. Before this step,
     * `onCreate` wrote [reportView]/[logView]/`lastReportText` directly,
     * bypassing [emitReport] entirely (finding #7's exact defect) and, as a
     * direct consequence, never calling the `Log.i` [emitReport] ends with
     * — the same class of bug [emitReport]'s own doc says it exists to
     * prevent. This function calls the SAME `Log.i` shape (labelled RESTORE
     * rather than REPORT, so a real report and a restore are still
     * distinguishable in logcat) and is now the ONLY other writer of
     * [reportView]/[logView]/[reportLog]'s state — a no-op (no Bundle, or a
     * Bundle with neither key) leaves both views at their XML defaults,
     * matching the pre-step-1 behaviour exactly. */
    private fun restoreReport(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        val text = savedInstanceState.getString(STATE_LAST_REPORT)
        val entries = savedInstanceState.getStringArrayList(STATE_LOG_ENTRIES)
        if (text == null && entries == null) return
        reportLog.restore(entries ?: emptyList(), lastText = text)
        if (text != null) reportView.text = reportLog.lastText
        if (entries != null) logView.text = reportLog.rendered(titleSizePx = logTitleSizePx())
        Log.i(TAG, "M2 stage: restored report/log across Activity recreation (text=${text != null}, log_entries=${entries?.size ?: 0})")
    }

    /** §6.2 item 16 (2026-09-01, second real-device fix — "one point bigger,
     * if it would remain light"): the log entry title line's target text
     * size, in raw pixels, for [ReportLog.rendered]'s `titleSizePx`. Derived
     * from [logView]'s OWN currently-configured text size (never a
     * hardcoded number, so this stays correct if the base size ever
     * changes) plus exactly one sp's worth of pixels at the device's
     * current font scale (`scaledDensity`, not `density` — an
     * `AbsoluteSizeSpan(..., dip = false)` call site expects raw pixels,
     * and computing the "+1" through `scaledDensity` is what makes it a
     * true "+1 sp", correct for a user with a non-default system font
     * scale, rather than a dp value that would drift from sp at any other
     * scale). NORMAL weight only — no bold; the owner asked for a size
     * bump, not `StyleSpan(Typeface.BOLD)`. */
    private fun logTitleSizePx(): Int {
        val onePointInPx = resources.displayMetrics.scaledDensity
        return (logView.textSize + onePointInPx).roundToInt()
    }

    /** §6.2 item 16 (D46): the log entry title — the verified request
     * origin's `host:port` (`scope_domain`, D37/D42), or the fixed,
     * value-free label for a scan with no VERIFIED handoff. Also used for a
     * handoff whose verification itself failed: an unverified/unconfirmed
     * origin is never shown as though it were a trusted site (D37/D42's
     * whole point is that origin trust requires verification — an entry
     * title is not exempt from that). [origin] is always a caller-supplied
     * value already resolved at the point of use (e.g. `verified.origin`),
     * never re-read from mutable state here, so the title can't drift from
     * what the entry actually describes. */
    private fun siteTitleFor(origin: String?): String {
        if (origin == null) return SITE_NO_HANDOFF
        val uri = runCatching { URI(origin) }.getOrNull() ?: return SITE_NO_HANDOFF
        val host = uri.host ?: return SITE_NO_HANDOFF
        return if (uri.port != -1) "$host:${uri.port}" else host
    }

    // 2026-09 real-device fix, owner decision ("Mode is redundant"): the
    // plain-block Mode line and its modeLabel() source were REMOVED —
    // Sent/Shared/Identity already state everything "mode A"/"mode B"
    // means in plain language (Sent: nothing left this device === mode A;
    // Identity: new/known-only-here === mode B), so a separate Mode line
    // only restated one of them. Standing fact this rests on: D21 is
    // "always read, conditionally mint" — mode never changes what is READ,
    // only what is SENT, so "mode A" must never be allowed to imply a
    // lesser read. Mode STAYS in the ▸ technical: line (chipAuthTechnical's
    // sibling, `mode: $mode` in continueAfterRead's baseReport) exactly as
    // before — only the plain-block line and DisclosureSummary.mode were
    // removed. The DERIVED mode display on the main screen
    // (refreshModeStatus / mode_status TextView, CHANGE 2) is UNRELATED and
    // unaffected — that is a different display for a different purpose.

    /** §6.2 item 16 (2026-09 real-device fix, "chip authenticity, three
     * states"): the plain-language value for the log entry's `Chip auth`
     * line. Reported to the owner for approval — see [ChipAuthStatus]'s
     * three states and why NOT_SUPPORTED must never read as "false". */
    private fun chipAuthLabel(status: M0Probe.ChipAuthStatus): String = when (status) {
        // Owner-revised wording (clone phrasing rejected as alarming to a
        // non-technical reader on every US-passport-shaped NOT_SUPPORTED
        // scan) — the three-state DISTINCTION itself (M0Probe.ChipAuthStatus,
        // the two independent try/catch blocks that stop conflating a
        // missing capability with a failed protocol) is unchanged; only
        // this text changed. "absent" must never render as "false".
        M0Probe.ChipAuthStatus.VERIFIED -> "Verified — this document's chip proved it is genuine"
        M0Probe.ChipAuthStatus.NOT_SUPPORTED -> "Not supported — this document has no chip authenticity check"
        M0Probe.ChipAuthStatus.FAILED -> "Not verified — the chip check did not pass"
    }

    /** §6.2 item 15 (D43, extended 2026-09 to cover SUCCESS too — "the one
     * outcome the user most wants confirmed was the only one that didn't
     * confirm itself"): the ONE place a blocking, value-free TERMINAL-
     * OUTCOME dialog is shown — a failure (the original D43 case) or a
     * confirmed mint success ([MintConfirmation]). Same mechanism for both,
     * deliberately not forked into two implementations. [message] is the
     * same value-free text already passed to [emitReport] (or a close
     * paraphrase of it) — never a new PII surface. The state transition
     * happens ONLY on dismissal, via the SAME `wipeSession` branch item 6
     * already defines ([FailureTransition.keepsMrzAndMode] pins which of
     * the THREE buckets applies — access-establishment failure, transient
     * chip-communication failure (2026-09), or reset; a success dialog
     * always resolves to the plain reset branch, the SAME
     * `wipeSession(keepMrzAndMode = false)` every other terminal outcome
     * uses — no separate post-success policy). A handoff-specific pointer
     * reset (pendingHandoff/verifiedRequest, which [wipeSession] itself
     * does not own — see its doc) happens first when the session is NOT
     * being kept, so [wipeSession] restores the derived mode display
     * correctly (2026-09: there is no longer a mode RADIO to re-enable —
     * see [refreshModeStatus]). Not cancelable: only OK dismisses (no
     * Snackbar, no outside-tap, no back-press dismissal), per D43.
     * @param isTransientChipCommunicationFailure bucket 2 (2026-09) — see
     *   [FailureTransition]'s doc. Defaults false; every call site except
     *   the one real read-failure path that can classify it leaves this at
     *   the default; always false for a success confirmation. */
    private fun showBlockingOutcomeDialog(message: String, isAccessEstablishmentFailure: Boolean, isTransientChipCommunicationFailure: Boolean = false) {
        val keepMrzAndMode = FailureTransition.keepsMrzAndMode(isAccessEstablishmentFailure, isTransientChipCommunicationFailure)
        AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                dialog.dismiss()
                if (!keepMrzAndMode) {
                    pendingHandoff = null
                    verifiedRequest = null
                }
                wipeSession(keepMrzAndMode = keepMrzAndMode)
            }
            .show()
    }

    /**
     * D55 — THE ONLY place in this file that writes `.visibility` on
     * [mainLayout], [logLayout] or [loadingLayout]. Enforced the same way
     * [emitReport] is the only place that writes [reportView]'s text: any
     * future call site that wants a different pane shown must set
     * [readInProgress] and/or the tab selection, then call this function —
     * never touch a view's `.visibility` directly.
     *
     * WHY: `activity_main.xml` places these three views as overlapping
     * siblings inside one `FrameLayout` — so more than one `VISIBLE` at
     * once means one draws over another. Before D55, four scattered
     * writes (the tab listener; `startSession`; `ReadTask.onPostExecute`)
     * each owned a different pair of these views and could disagree with
     * each other, which is exactly how a user got stranded on the Log tab
     * with no way back to the MRZ form — see [PaneVisibility]'s class doc
     * for the full root cause. This function always sets all THREE views
     * on every call, from [PaneVisibility.choosePane]'s single decision,
     * so the both-visible state is unrepresentable rather than merely
     * avoided by convention.
     */
    private fun showPane() {
        val pane = PaneVisibility.choosePane(readInProgress, tabLayout.selectedTabPosition)
        mainLayout.visibility = if (pane == PaneVisibility.Pane.SCAN) View.VISIBLE else View.GONE
        logLayout.visibility = if (pane == PaneVisibility.Pane.LOG) View.VISIBLE else View.GONE
        loadingLayout.visibility = if (pane == PaneVisibility.Pane.LOADING) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------- session
    private fun startSession(isoDep: IsoDep, bacKey: BACKeySpec, mode: PresentationMode) {
        readInProgress = true
        showPane()
        ReadTask(isoDep, bacKey, mode).execute()
    }

    private inner class ReadTask(
        private val isoDep: IsoDep,
        private val bacKey: BACKeySpec,
        private val mode: PresentationMode,
    ) : AsyncTask<Void?, Void?, Exception?>() {

        private lateinit var dg1File: DG1File
        private lateinit var sodFile: SODFile
        private val timeline = M0Probe.Timeline()
        private var accessProtocol = "unknown"
        private var chipAuthStatus = M0Probe.ChipAuthStatus.NOT_SUPPORTED
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

                // 2026-09 real-device fix (second round — a real bug):
                // this block no longer sets a "this is an access failure"
                // flag from the mere fact that an exception happened here.
                // A tag-loss mid-PACE/mid-BAC would previously be mislabelled
                // an access-establishment failure ("check your details")
                // when the true cause was a card slip. The exception is
                // simply rethrown uninterpreted; classification happens
                // ONCE, in onPostExecute, from the exception's own evidence
                // via FailureTransition.classify — see that object's doc.
                service.sendSelectApplet(paceSucceeded)
                if (!paceSucceeded) {
                    try {
                        service.getInputStream(PassportService.EF_COM).read()
                    } catch (e: Exception) {
                        service.doBAC(bacKey)
                    }
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
                //
                // 2026-09 real-device fix ("three states, not two"): the DG14
                // READ (does this document carry the file at all — NOT_SUPPORTED
                // if not) and the doEACCA() CHALLENGE-RESPONSE ITSELF (FAILED if
                // the file/security-info IS present but the protocol errors)
                // are now two INDEPENDENT try/catch blocks — before this
                // restructure both landed in the same catch and were
                // indistinguishable, exactly the "absent" vs "failed"
                // conflation the owner flagged.
                var caStatus = M0Probe.ChipAuthStatus.NOT_SUPPORTED
                try {
                    val dg14In = service.getInputStream(PassportService.EF_DG14)
                    val dg14Encoded = IOUtils.toByteArray(dg14In)
                    val dg14File = org.jmrtd.lds.icao.DG14File(ByteArrayInputStream(dg14Encoded))
                    val caInfos = dg14File.securityInfos.filterIsInstance<org.jmrtd.lds.ChipAuthenticationPublicKeyInfo>()
                    caStatus = if (caInfos.isEmpty()) {
                        M0Probe.ChipAuthStatus.NOT_SUPPORTED
                    } else {
                        try {
                            for (si in caInfos) {
                                service.doEACCA(si.keyId, org.jmrtd.lds.ChipAuthenticationPublicKeyInfo.ID_CA_ECDH_AES_CBC_CMAC_256, si.objectIdentifier, si.subjectPublicKey)
                            }
                            M0Probe.ChipAuthStatus.VERIFIED
                        } catch (e: Exception) {
                            Log.i(TAG, "M2 stage: CA declared (DG14 present) but failed (${e.javaClass.simpleName})")
                            M0Probe.ChipAuthStatus.FAILED
                        }
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "M2 stage: CA not supported (no DG14) (${e.javaClass.simpleName})")
                }
                val aa = M0Probe.tryActiveAuth(service, sodFile)
                // Either mechanism verifying is enough to call chip authenticity
                // VERIFIED (unchanged combining rule); otherwise FAILED beats
                // NOT_SUPPORTED (a genuine protocol failure is a stronger signal
                // than "this mechanism just isn't present") — the combined
                // status is never NOT_SUPPORTED unless BOTH mechanisms are.
                chipAuthStatus = when {
                    caStatus == M0Probe.ChipAuthStatus.VERIFIED || aa.first == M0Probe.ChipAuthStatus.VERIFIED -> M0Probe.ChipAuthStatus.VERIFIED
                    caStatus == M0Probe.ChipAuthStatus.FAILED || aa.first == M0Probe.ChipAuthStatus.FAILED -> M0Probe.ChipAuthStatus.FAILED
                    else -> M0Probe.ChipAuthStatus.NOT_SUPPORTED
                }
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
            // D55: FIRST statement, on every exit path (including the
            // early `return` below in the failure branch) — see
            // [showPane]'s doc. No future branch can add a new exit
            // without also clearing [readInProgress], because there is
            // nothing left after this point that still needs it true.
            readInProgress = false
            showPane()
            if (result != null) {
                // 2026-09 real-device fix, second round (a real bug: a
                // mid-PACE/mid-BAC tag-loss used to be mislabelled an
                // access-establishment failure because the OLD rule was
                // "any exception during this code path"). Classification is
                // now by EXCEPTION EVIDENCE alone, in ONE place —
                // FailureTransition.classify — with transient checked
                // FIRST; see that object's doc for the full precedence
                // rationale and why the state-transition mapping itself
                // (keepsMrzAndMode) is unaffected.
                val classification = FailureTransition.classify(result)
                val accessFailure = classification == FailureTransition.Classification.ACCESS_ESTABLISHMENT
                val transientChipFailure = classification == FailureTransition.Classification.TRANSIENT_CHIP_COMMUNICATION
                // §6.2 item 15 (D43): the report renders immediately (so it
                // is never missed, even before the dialog is dismissed), but
                // the state transition (wipeSession) happens only on OK —
                // see showBlockingOutcomeDialog's doc. No Snackbar for this
                // outcome any more.
                val reason = when {
                    accessFailure -> getString(R.string.error_read)
                    transientChipFailure -> TRANSIENT_READ_FAILURE_MESSAGE
                    else -> "Document read failed: ${result.javaClass.simpleName}: ${result.message}"
                }
                emitReport(
                    "verdict: FAIL\nfailure: ${result.javaClass.simpleName}: ${result.message}\n$masterlistReport",
                    ReportLog.DisclosureSummary(
                        site = siteTitleFor(verifiedRequest?.origin),
                        result = when {
                            accessFailure -> "Couldn't read — check your details"
                            transientChipFailure -> "Couldn't read — card moved"
                            else -> "Read failed — the document could not be read"
                        },
                        sent = "nothing left this device",
                        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                    ),
                )
                showBlockingOutcomeDialog(
                    reason,
                    isAccessEstablishmentFailure = accessFailure,
                    isTransientChipCommunicationFailure = transientChipFailure,
                )
                return
            }
            // Any completed read (success or a real masterlist "no") wipes the
            // session — only an access-establishment failure (or, as of
            // 2026-09, a transient chip-communication failure) keeps it (F3).
            wipeSession(keepMrzAndMode = false)
            continueAfterRead(mode, passiveAuthVerdict!!, chipAuthStatus, accessProtocol, masterlistReport, dg1File)
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
        chipAuthStatus: M0Probe.ChipAuthStatus,
        accessProtocol: String,
        masterlistReport: String,
        dg1File: DG1File,
    ) {
        // 2026-09 real-device fix: the technical D21 payload field now has a
        // genuine third value ("failed") where before it only ever said
        // "passed"/"absent" — see M0Probe.ChipAuthStatus's doc. Single
        // source: this string and the plain-language chipAuthLabel() line
        // below are both derived from the SAME chipAuthStatus, never two
        // independently-tracked variables that could drift.
        val chipAuthTechnical = when (chipAuthStatus) {
            M0Probe.ChipAuthStatus.VERIFIED -> "passed"
            M0Probe.ChipAuthStatus.FAILED -> "failed"
            M0Probe.ChipAuthStatus.NOT_SUPPORTED -> "absent"
        }
        val baseReport = buildString {
            append("mode: $mode\n")
            append("access_protocol: $accessProtocol\n")
            append("chip_auth (D21 payload field): $chipAuthTechnical\n")
            append(masterlistReport).append("\n")
            append("passive_auth: $verdict\n")
        }

        // Single source of truth (MintGate) — see its doc for the root-cause
        // note on why the branch below now goes through emitReport.
        val mayMint = MintGate.mayMint(mode == PresentationMode.B, verdict)
        if (!mayMint) {
            emitReport(
                baseReport + "\nmint_gate: NOT MET — evidence: [] (D27${if (mode == PresentationMode.B) ", item 3: masterlist/passive-auth gate not satisfied" else ""})\nverdict: ${if (verdict.ok) "PASS (read)" else "FAIL (could not check)"}",
                ReportLog.DisclosureSummary(
                    site = siteTitleFor(verifiedRequest?.origin),
                    result = when {
                        !verdict.ok -> "Read finished, but the document could not be verified"
                        mode == PresentationMode.A -> "Read OK — nothing sent"
                        else -> "Read OK, but this document is not accepted for age verification"
                    },
                    chipAuthenticity = chipAuthLabel(chipAuthStatus),
                    sent = "nothing left this device",
                    shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                ),
            )
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
            emitReport(
                baseReport + "\nmint: REFUSED — mode B requires a verified request origin to scope the attester key to (D38); no handoff is pending",
                ReportLog.DisclosureSummary(
                    site = SITE_NO_HANDOFF,
                    result = "Refused — mode B needs an active site request to continue",
                    chipAuthenticity = chipAuthLabel(chipAuthStatus),
                    sent = "nothing left this device",
                    shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                ),
            )
            showBlockingOutcomeDialog(
                "Mode B requires a verified handoff request to scope the device key to — no handoff is pending.",
                isAccessEstablishmentFailure = false,
            )
            return
        }

        // 2026-09-01 real-device fix (owner's Pixel 6a run, "fix 3" — a spent/
        // aged handoff session inviting an unwinnable second tap): belt-and-
        // suspenders re-check right before minting, in case the session aged
        // out DURING the chip read (the earlier, cheaper check is up front in
        // [lockModeAndArm] — see its doc). Both checks read the SAME
        // `zkagent.challenge.expires_at` field off the already-verified
        // request object via [RequestTrust.expiresAtOf]/[isExpired] — no
        // network round-trip, nothing invented.
        val verifiedForExpiry = verifiedRequest
        if (mode == PresentationMode.B && verifiedForExpiry != null) {
            val expiresAt = RequestTrust.expiresAtOf(verifiedForExpiry.json)
            if (expiresAt != null && RequestTrust.isExpired(expiresAt, System.currentTimeMillis())) {
                Log.e(TAG, "M2 stage: mode B mint REFUSED — verification session expired before minting (expires_at=$expiresAt)")
                emitReport(
                    baseReport + "\nmint: REFUSED — verification session expired before minting could complete (expires_at=$expiresAt)",
                    ReportLog.DisclosureSummary(
                        site = siteTitleFor(verifiedForExpiry.origin),
                        result = "Refused — verification session expired",
                        chipAuthenticity = chipAuthLabel(chipAuthStatus),
                        sent = "nothing left this device",
                        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                    ),
                )
                showBlockingOutcomeDialog(SESSION_EXPIRED_MESSAGE, isAccessEstablishmentFailure = false)
                return
            }
        }

        // §6.2 item 16 (D46): [site] is derived ONCE here, from the SAME
        // verified origin [origin] this mint is already scoped to (D38/D42)
        // — never re-read from mutable state downstream — and threaded
        // through the mint pipeline the same way zktag/scopeDomain already
        // are (see the Thread block below's doc).
        val site = siteTitleFor(origin)
        // 2026-09-01 real-device fix ("fix 2" — the stale "In progress"
        // entry): one fresh id per mint attempt, threaded through every
        // emitReport call below (Thread block, promptAndMint,
        // mintAndMaybeHandoff) as a parameter, the same discipline already
        // used for site/zktag/scopeDomain — never re-derived downstream, so
        // the eventual terminal outcome (whichever of the several possible
        // endings it turns out to be) always replaces THIS SAME "In
        // progress" entry, never appends a second one. See ReportLog.append's doc.
        val attemptId = java.util.UUID.randomUUID().toString()
        emitReport(
            baseReport + "\nmint_gate: MET — requesting biometric/device-credential authorization before minting (item 2)\n",
            ReportLog.DisclosureSummary(
                site = site,
                result = "In progress — waiting for you to authorize with biometrics or a device PIN",
                chipAuthenticity = chipAuthLabel(chipAuthStatus),
                sent = "nothing yet",
                shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing yet"),
            ),
            attemptId = attemptId,
            pending = true,
        )
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
                runOnUiThread {
                    emitReport(
                        baseReport + "\nmint: FAILED — no verified request object to mint against (D38)",
                        ReportLog.DisclosureSummary(
                            site = SITE_NO_HANDOFF,
                            result = "Failed — the site's request disappeared before this could finish",
                            chipAuthenticity = chipAuthLabel(chipAuthStatus),
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                        attemptId = attemptId,
                    )
                    showBlockingOutcomeDialog("No verified handoff request to mint against.", isAccessEstablishmentFailure = false)
                }
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
                runOnUiThread {
                    emitReport(
                        baseReport + "\nmint: FAILED — verified origin has no parseable host",
                        ReportLog.DisclosureSummary(
                            site = site,
                            result = "Failed — could not identify the site's address",
                            chipAuthenticity = chipAuthLabel(chipAuthStatus),
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                        attemptId = attemptId,
                    )
                    showBlockingOutcomeDialog("The verified handoff request's origin has no parseable host.", isAccessEstablishmentFailure = false)
                }
                return@Thread
            }
            val candidates = M0Probe.deriveCandidates(dg1File, null, null, domain = scopeDomain)
            val zktag = candidates["document_number"]
            if (zktag == null) {
                Log.w(TAG, "M2 stage: no document_number field to derive zktag from (D9)")
                runOnUiThread {
                    emitReport(
                        baseReport + "\nmint: FAILED — no document_number field to derive from (D9)",
                        ReportLog.DisclosureSummary(
                            site = site,
                            result = "Failed — this document type is not supported for site verification",
                            chipAuthenticity = chipAuthLabel(chipAuthStatus),
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                        attemptId = attemptId,
                    )
                    showBlockingOutcomeDialog("This document has no document-number field to derive a zktag from.", isAccessEstablishmentFailure = false)
                }
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
                runOnUiThread {
                    emitReport(
                        baseReport + "\nmint: FAILED — device key generation threw ${e.javaClass.simpleName}: ${e.message}",
                        ReportLog.DisclosureSummary(
                            site = site,
                            result = "Failed — could not create a device key for this site",
                            chipAuthenticity = chipAuthLabel(chipAuthStatus),
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                        attemptId = attemptId,
                    )
                    showBlockingOutcomeDialog("Device key generation failed: ${e.javaClass.simpleName}: ${e.message}", isAccessEstablishmentFailure = false)
                }
                return@Thread
            }
            runOnUiThread { promptAndMint(keyState, baseReport, zktag, scopeDomain, site, attemptId, mode, chipAuthStatus) }
        }.start()
    }

    private fun promptAndMint(keyState: DeviceKey.KeyState, baseReport: String, zktag: String, scopeDomain: String, site: String, attemptId: String, mode: PresentationMode, chipAuthStatus: M0Probe.ChipAuthStatus) {
        val sig = DeviceKey.initSignature(keyState)
        if (sig == null) {
            Log.w(TAG, "M2 stage: DeviceKey.initSignature returned null — no usable device key/signature")
            emitReport(
                baseReport + "\nmint: FAILED — no usable device key/signature (see algorithm matrix in logcat)",
                ReportLog.DisclosureSummary(
                    site = site,
                    result = "Failed — no usable device key or signature is available on this device",
                    chipAuthenticity = chipAuthLabel(chipAuthStatus),
                    sent = "nothing left this device",
                    shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                ),
                attemptId = attemptId,
            )
            showBlockingOutcomeDialog("No usable device key or signature is available on this device.", isAccessEstablishmentFailure = false)
            return
        }
        // Finding #11 (.claude/remember/findings.md) FIX, provisional pending
        // owner approval: the title names the verified requesting site —
        // [site] is the SAME siteTitleFor(verified.origin) value already
        // computed for the log entry title (D46), threaded through
        // unchanged; see MintPromptText's doc for why the DECISION lives
        // there and this call site stays a thin getString() applier.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title_for_site, MintPromptText.titleFor(site)))
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
                    Thread { mintAndMaybeHandoff(keyState, authorizedSig, baseReport, zktag, scopeDomain, site, attemptId, mode, chipAuthStatus) }.start()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    emitReport(
                        baseReport + "\nmint: REFUSED — biometric/device-credential error $errorCode: $errString\nverdict: PASS (read only, no mint)",
                        ReportLog.DisclosureSummary(
                            site = site,
                            result = "Cancelled — biometric/device-credential authorization did not complete",
                            chipAuthenticity = chipAuthLabel(chipAuthStatus),
                            sent = "nothing left this device",
                            shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                        ),
                        attemptId = attemptId,
                    )
                    showBlockingOutcomeDialog("Biometric/device-credential authorization error $errorCode: $errString", isAccessEstablishmentFailure = false)
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
     *   used to derive [keyState]'s alias.
     * @param site likewise, [siteTitleFor] of the same verified origin —
     *   the §6.2 item 16 (D46) log-entry title. */
    private fun mintAndMaybeHandoff(keyState: DeviceKey.KeyState, signature: Signature, baseReport: String, zktag: String, scopeDomain: String, site: String, attemptId: String, mode: PresentationMode, chipAuthStatus: M0Probe.ChipAuthStatus) {
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
            runOnUiThread {
                emitReport(
                    baseReport + "\nmint: local signature OK, but handoff: REFUSED — no verified request object to mint against (D38)",
                    ReportLog.DisclosureSummary(
                        site = SITE_NO_HANDOFF,
                        result = "Failed — the site's request disappeared before this could finish",
                        chipAuthenticity = chipAuthLabel(chipAuthStatus),
                        sent = "nothing left this device",
                        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                    ),
                    attemptId = attemptId,
                )
                showBlockingOutcomeDialog("No verified handoff request to mint against.", isAccessEstablishmentFailure = false)
            }
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
            runOnUiThread {
                emitReport(
                    baseReport + "\nmint: FAILED — could not read the device key's public key bytes (D38 evidence requires pubkey)",
                    ReportLog.DisclosureSummary(
                        site = site,
                        result = "Failed — could not read your device key to sign the proof",
                        chipAuthenticity = chipAuthLabel(chipAuthStatus),
                        sent = "nothing left this device",
                        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                    ),
                    attemptId = attemptId,
                )
                showBlockingOutcomeDialog("Could not read the device key's public key bytes.", isAccessEstablishmentFailure = false)
            }
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
        //
        // §6.2 item 16 (D46): [deliveryResult] tracks which of the four
        // delivery sub-outcomes actually happened, so the log entry's
        // Result line is accurate per outcome — the local signature always
        // succeeds by the time we reach this block, but "sent" only means
        // the site actually received it (HTTP 2xx). A transport failure
        // must never be reported as "Verified".
        var deliveryResult: DeliveryResult
        try {
            val presentation = HandoffClient.buildPresentation("B", claim, challenge, zktag, listOf(evidence))
            val responseUri = requestObject.optString("response_uri", "").ifEmpty { null }
            if (responseUri == null) {
                Log.w(TAG, "M2 stage: handoff request object carries no top-level response_uri — cannot direct_post")
                report += "handoff: FAILED — request object carries no response_uri\n"
                deliveryResult = DeliveryResult.NoResponseUri
            } else {
                val state = if (requestObject.has("state")) requestObject.getString("state") else null
                Log.i(TAG, "M2 stage: handoff direct_post -> $responseUri (state_present=${state != null})")
                val result = HandoffClient.postDirectPost(responseUri, state, presentation)
                if (result.httpStatus !in 200..299) {
                    Log.w(TAG, "M2 stage: handoff direct_post response NON-2xx http_status=${result.httpStatus} body=${result.body}")
                    deliveryResult = DeliveryResult.Rejected(result.httpStatus)
                } else {
                    Log.i(TAG, "M2 stage: handoff direct_post response http_status=${result.httpStatus} body=${result.body}")
                    deliveryResult = DeliveryResult.Accepted
                }
                report += "handoff: direct_post http_status=${result.httpStatus} -> ${result.body}\n"
            }
        } catch (e: Exception) {
            Log.e(TAG, "M2 stage: handoff direct_post FAILED", e)
            report += "handoff: FAILED ${e.javaClass.simpleName}: ${e.message}\n"
            deliveryResult = DeliveryResult.TransportFailed
        }
        // Handoff has now definitively completed or failed — clear on the
        // main thread (item 6 lifecycle discipline). verifiedRequest is
        // cleared in lockstep — it has no meaning without a pendingHandoff.
        // 2026-09-01 real-device fix ("fix 3"): this is what makes a spent
        // session actually GO AWAY — a single-use challenge/nonce that
        // reached direct_post (Accepted, Rejected, or the site simply never
        // got it — NoResponseUri/TransportFailed) cannot be meaningfully
        // resubmitted by THIS app either way (there is no retry UI), so
        // clearing on every one of the four outcomes — not only Accepted —
        // is the safer choice: it never leaves the mode locked to a session
        // this app has already given up on, which is exactly the "invites
        // another document tap that cannot succeed" defect from the owner's
        // Pixel 6a run.
        runOnUiThread {
            pendingHandoff = null
            verifiedRequest = null
            refreshModeStatus()
        }
        report += "\nverdict: PASS (minted)"

        // §6.2 item 16 (D46/D48): Sent/Shared describe what a successful
        // LOCAL sign produces — this is true regardless of delivery outcome,
        // the device did sign a claim over the site's requested predicate
        // and nothing else. Only the Result line (and, for a delivery
        // failure, a corrected Sent/Shared line) changes per
        // [deliveryResult], so a transport failure is never reported as a
        // success.
        val identity = if (keyState.reusedExistingKey) {
            // D47/D48, owner-confirmed verbatim — "only here" is
            // load-bearing (D38/D39 per-(origin,zktag) key isolation: this
            // site recognizes the returning user, no other site can). MUST
            // NOT be simplified/shortened out.
            "known — recognized only here from previous visit"
        } else {
            "new — minted fresh for this site"
        }
        // §6.2 item 16 (D48): the Shared line is a QUESTION -> ANSWER record
        // of the SIGNED claim, read from the SAME [claim] map that gets
        // canonicalized and signed above (`claim["threshold"]`,
        // `claim["over_threshold"]`) — never a separate constant, never the
        // literal 18 duplicated here. This is the correct SOURCE even though
        // D48 ultimately wants the threshold read from the verified request
        // object: nothing in the request object or the app carries a real
        // threshold or a computed over/under answer today (escalated and
        // owner-acknowledged; tracked as an open PRD question, not this
        // round's job to close) — the signed claim map is the single source
        // of truth for what was ACTUALLY SENT, so this rendering is a
        // faithful, by-construction record of the disclosure today, and
        // becomes correct automatically the moment a real threshold/answer
        // exist, with no change to this rendering code.
        val disclosedThreshold = claim["threshold"]
        val disclosedAnswer = claim["over_threshold"]
        // §6.2 item 16 (D48): the predicate label is "age > <N>" — the
        // owner's exact shape, so this line and the Sent line below can
        // never drift from each other (both built from the same
        // disclosedPredicate string, sourced from the same claim map).
        val disclosedPredicate = "age > $disclosedThreshold"
        val sharedClaim = ReportLog.DisclosureSummary.Claim(predicate = disclosedPredicate, answer = "$disclosedAnswer")
        val sentClaimLine = "a site-only pseudonym + a signed claim ($disclosedPredicate: $disclosedAnswer)"
        // §6.2 item 16 (D24, D48): stated in every claim, per D24's bare-mode
        // requirement — this app's evidence plugs (sig-ed25519/1,
        // sig-p256/1) prove WHO signed the claim (the device/key), never
        // that the claim's content is true; no ZK circuit ties it to the
        // document. Subordinate technical note, not part of the
        // plain-language block.
        val claimProofNote = "claim_proof: self-asserted by the device — not independently proven (D24)"
        val summary = when (deliveryResult) {
            is DeliveryResult.Accepted -> ReportLog.DisclosureSummary(
                site = site,
                result = "Verified — the site accepted you",
                chipAuthenticity = chipAuthLabel(chipAuthStatus),
                sent = sentClaimLine,
                shared = ReportLog.DisclosureSummary.Shared.Disclosed(listOf(sharedClaim)),
                identity = identity,
                technicalNote = claimProofNote,
            )
            is DeliveryResult.Rejected -> ReportLog.DisclosureSummary(
                site = site,
                result = "Signed OK, but the site rejected the response (HTTP ${deliveryResult.httpStatus})",
                chipAuthenticity = chipAuthLabel(chipAuthStatus),
                sent = "a signed proof was prepared, but the site did not accept it",
                shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing the site kept — it rejected the response"),
                identity = identity,
                technicalNote = claimProofNote,
            )
            DeliveryResult.NoResponseUri -> ReportLog.DisclosureSummary(
                site = site,
                result = "Signed OK, but there was no site address to send it to",
                chipAuthenticity = chipAuthLabel(chipAuthStatus),
                sent = "nothing reached the site",
                shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                identity = identity,
                technicalNote = claimProofNote,
            )
            DeliveryResult.TransportFailed -> ReportLog.DisclosureSummary(
                site = site,
                result = "Signed OK, but sending the result to the site failed",
                chipAuthenticity = chipAuthLabel(chipAuthStatus),
                sent = "nothing reached the site — delivery failed",
                shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
                identity = identity,
                technicalNote = claimProofNote,
            )
        }
        runOnUiThread {
            emitReport(report, summary, attemptId = attemptId)
            // 2026-09 real-device fix ("confirm success too" — the owner's
            // three follow-up scans were all genuine successes with nothing
            // telling him to go back to the browser): ONLY a genuinely
            // accepted delivery confirms itself with a blocking modal — see
            // MintConfirmation's doc for why the other three DeliveryResult
            // outcomes must never reach here. Minimal, owner-specified
            // wording: just the outcome, nothing this dialog's own log
            // entry (right above) already says in full. Dismissal follows
            // the SAME plain-reset branch every other terminal outcome
            // uses — no separate post-success policy.
            if (MintConfirmation.confirmsSuccess(deliveryAccepted = deliveryResult is DeliveryResult.Accepted)) {
                showBlockingOutcomeDialog(MINT_CONFIRMED_MESSAGE, isAccessEstablishmentFailure = false)
            }
        }
    }

    /** §6.2 item 16 (D46): which of the four ways `mintAndMaybeHandoff`'s
     * `direct_post` attempt can end, so the log entry's Result line is
     * accurate per outcome rather than a fixed "Verified" regardless of
     * whether the site actually received anything. */
    private sealed class DeliveryResult {
        object Accepted : DeliveryResult() // HTTP 2xx
        data class Rejected(val httpStatus: Int) : DeliveryResult() // non-2xx
        object NoResponseUri : DeliveryResult()
        object TransportFailed : DeliveryResult() // network/other exception
    }

    // -------------------------------------------------------- masterlist UI
    /** §6.2 item 16 (D46): the debug-only probe buttons below are NOT a
     * document scan — no site is involved and nothing is ever disclosed to
     * one — so they get this fixed, minimal summary rather than the
     * SITE_NO_HANDOFF label's scan-shaped semantics or an invented site
     * name. [failed] is whether the probe's own text contains a failure
     * marker, read from the SAME string already built for [reportView] —
     * never a second judgment about what happened. */
    private fun diagnosticSummary(failed: Boolean, label: String) = ReportLog.DisclosureSummary(
        site = SITE_NO_HANDOFF,
        result = if (failed) "Diagnostic failed — $label" else "Diagnostic OK — $label",
        sent = "nothing left this device",
        shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"),
    )

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
        val text = log.toString()
        runOnUiThread { emitReport(text, diagnosticSummary(failed = text.contains("PROBE FAILED") || text.contains("INVALID RUN"), label = "masterlist checks")) }
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
        val text = log.toString()
        runOnUiThread { emitReport(text, diagnosticSummary(failed = text.contains("PROBE FAILED") || text.contains("MISMATCH") || text.contains("UNEXPECTED"), label = "device key self-test")) }
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
        private const val STATE_LOG_ENTRIES = "m2_log_entries"
        // §6.2 item 16 (D46): the exact, owner-specified fixed label for a
        // log entry with no verified handoff — never a blank field or a
        // fabricated origin.
        private const val SITE_NO_HANDOFF = "Local scan (no site)"
        // 2026-09-01 real-device fix ("fix 3"), owner's words for the intent
        // ("verifier session expired or something") — exact user-facing copy,
        // not yet owner-approved (report requested back explicitly). Used by
        // both the up-front check ([lockModeAndArm]) and the belt-and-
        // suspenders re-check right before minting ([continueAfterRead]), so
        // the two can never say something different for the same condition.
        private const val SESSION_EXPIRED_MESSAGE = "Verification session expired — reopen the link from the site."
        // 2026-09 real-device fix ("fix — a hand tremor should not cost a
        // retype"), bucket 2 of FailureTransition's three-bucket rule. Not
        // yet owner-approved (report requested back explicitly): tells the
        // user what to DO, not just that something failed.
        private const val TRANSIENT_READ_FAILURE_MESSAGE = "Couldn't read — keep the card at the top of your phone."
        // 2026-09 real-device fix ("confirm success too"). Owner-specified
        // shape: minimal, just the outcome — no restated site/predicate/
        // identity detail (that already lives in the log entry and
        // report). Not yet owner-approved (report requested back
        // explicitly): reuses the exact Result-line wording for this same
        // outcome, so there is only one phrase to approve, not two.
        // Owner-supplied wording, deliberately DIFFERENT from the Result
        // line for this same outcome ("Verified — the site accepted you")
        // — the two are not meant to match; do not propagate one into the
        // other.
        private const val MINT_CONFIRMED_MESSAGE = "ID scanned successfully"

        // Finding #10 (.claude/remember/findings.md) MITIGATION, not yet
        // owner-approved (report requested back explicitly, per this
        // project's rule that every user-facing string goes to the owner):
        // shown in a Snackbar (non-terminal, no state transition — see
        // handleIncomingIntent's doc) when HandoffAdmission refuses an
        // inbound av:// handoff because a session is already locked or a
        // read is in progress. Shortened 2026-09-02 to fit a Snackbar.
        private const val HANDOFF_REFUSED_MID_SESSION_MESSAGE = "Ignored a site request that arrived mid-scan."

        // D56: per-process salt for [lastMrzHash] — a companion-object
        // field (not per-Activity-instance) so an Activity re-creation
        // (config change / background memory reclaim) does not invalidate
        // the previous-attempt comparison by silently re-salting it, which
        // would make every post-recreation attempt read as "first attempt"
        // regardless of whether the MRZ actually changed. Generated ONCE
        // per process, held ONLY in memory, NEVER logged, rendered, or
        // persisted — see [MrzChangeTracker]'s class doc for why an
        // unsalted digest of a short document number would itself be PII.
        private val mrzHashSalt: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    }
}
