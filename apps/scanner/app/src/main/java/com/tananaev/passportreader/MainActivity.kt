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
 */
abstract class MainActivity : AppCompatActivity() {

    // ---- presentation mode: see class doc item 4 ----
    private enum class PresentationMode { A, B }
    /** Set ONLY by [lockModeAndArm]. Read everywhere else that needs mode. */
    private var lockedMode: PresentationMode? = null

    // ---- handoff: see class doc item 8 / HandoffClient ----
    private var pendingHandoff: HandoffClient.PendingHandoff? = null

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

    // ---------------------------------------------------------------- item 4
    /** The ONE call site in this file that reads [modeGroup]. Disables the
     * control the same instant it is read, then arms NFC dispatch. */
    private fun lockModeAndArm() {
        if (lockedMode != null) return // already locked this session
        val passportRaw = passportNumberView.text?.toString()
        val expirationRaw = expirationDateView.text?.toString()
        val birthRaw = birthDateView.text?.toString()
        if (passportRaw.isNullOrEmpty() || expirationRaw.isNullOrEmpty() || birthRaw.isNullOrEmpty()) {
            Snackbar.make(passportNumberView, R.string.error_input, Snackbar.LENGTH_SHORT).show()
            return
        }
        lockedMode = if (modeGroup.checkedRadioButtonId == R.id.mode_b) PresentationMode.B else PresentationMode.A
        modeGroup.isEnabled = false
        for (i in 0 until modeGroup.childCount) modeGroup.getChildAt(i).isEnabled = false
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
                pendingHandoff = handoff
                handoffStatus.text = "Handoff request received (av://) — fill in your document details, lock a mode and scan to answer it."
                Log.i(TAG, "M2 stage: pendingHandoff captured from av:// intent")
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
        pendingHandoff = handoff
        handoffManualInput.text?.clear()
        handoffStatus.text = "Handoff request captured — fill in your document details, lock a mode and scan to answer it."
    }

    /** §6.2 item 6: MRZ + [lockedMode] are kept ONLY on an access-establishment
     * failure. Every other case (success, or a later-stage failure) wipes both. */
    private fun wipeSession(keepMrzAndMode: Boolean) {
        if (!keepMrzAndMode) {
            passportNumberView.text?.clear()
            expirationDateView.text?.clear()
            birthDateView.text?.clear()
            lockedMode = null
            modeGroup.isEnabled = true
            for (i in 0 until modeGroup.childCount) modeGroup.getChildAt(i).isEnabled = true
            lockButton.isEnabled = true
            modeLockedBanner.visibility = View.GONE
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

        emitReport(baseReport + "\nmint_gate: MET — requesting biometric/device-credential authorization before minting (item 2)\n")
        Thread {
            val keyState = try {
                DeviceKey.ensureKey(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "M2 stage: DeviceKey.ensureKey threw", e)
                runOnUiThread { emitReport(baseReport + "\nmint: FAILED — device key generation threw ${e.javaClass.simpleName}: ${e.message}") }
                return@Thread
            }
            runOnUiThread { promptAndMint(keyState, dg1File, baseReport) }
        }.start()
    }

    private fun promptAndMint(keyState: DeviceKey.KeyState, dg1File: DG1File, baseReport: String) {
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
                    mintAndMaybeHandoff(keyState, authorizedSig, dg1File, baseReport)
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

    /** Derives the zktag (SAME field as M0/D9: `document_number`, chip data
     * only — NEVER the device key), signs the item-9 evidence message with
     * the ALREADY-AUTHORIZED device key, and — if a [pendingHandoff] is
     * queued — POSTs `direct_post`. zktag is never fully rendered on screen;
     * only a truncated hash, matching this project's value-free logging
     * discipline elsewhere. */
    private fun mintAndMaybeHandoff(keyState: DeviceKey.KeyState, signature: Signature, dg1File: DG1File, baseReport: String) {
        val scopeDomain = pendingHandoff?.let {
            runCatching { Uri.parse(it.requestUri).host }
                .onFailure { e -> Log.w(TAG, "M2 stage: could not parse handoff request_uri host: ${e.javaClass.simpleName}: ${e.message}") }
                .getOrNull()
        } ?: "reference-app.test"
        val candidates = M0Probe.deriveCandidates(dg1File, null, null, domain = scopeDomain)
        val zktag = candidates["document_number"]
        if (zktag == null) {
            Log.w(TAG, "M2 stage: no document_number field to derive zktag from (D9)")
            emitReport(baseReport + "\nmint: FAILED — no document_number field to derive from (D9)")
            return
        }
        val threshold = 18
        val claim = mapOf("over_threshold" to true, "threshold" to threshold)
        val handoff = pendingHandoff
        val challenge: JSONObject
        val nonce: String
        if (handoff != null) {
            Log.i(TAG, "M2 stage: handoff pending — fetching request_uri=${handoff.requestUri}")
            try {
                val fetched = HandoffClient.fetchRequest(handoff.requestUri)
                Log.i(TAG, "M2 stage: handoff request fetched — http_status=${fetched.httpStatus} was_signed=${fetched.wasSigned} signature_verified=${fetched.signatureVerified}")
                val zkagent = fetched.json.optJSONObject("zkagent") ?: fetched.json
                challenge = zkagent.optJSONObject("challenge") ?: JSONObject()
                nonce = challenge.optString("nonce", "")
                Log.i(TAG, "M2 stage: handoff challenge parsed — nonce_present=${nonce.isNotEmpty()} response_uri_present=${challenge.has("response_uri")}")
            } catch (e: Exception) {
                Log.e(TAG, "M2 stage: handoff request fetch FAILED", e)
                emitReport(baseReport + "\nmint: local signature OK, but handoff request fetch FAILED: ${e.javaClass.simpleName}: ${e.message}")
                return
            }
        } else {
            // No queued handoff — mint locally only (demonstrates the D30
            // evidence path end-to-end without a live verifier). Nonce must
            // still be present for the item-9 formula; a fresh random nonce
            // stands in (never persisted, never reused).
            Log.i(TAG, "M2 stage: no pending handoff — minting locally only, fresh random nonce")
            val randomNonce = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            nonce = android.util.Base64.encodeToString(randomNonce, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            challenge = JSONObject().put("nonce", nonce).put("threshold", threshold)
        }

        val message = EvidenceSigner.messageFor(keyState.algorithm, claim, nonce, scopeDomain, zktag)
        val pubDer = DeviceKey.currentPublicKeyDer()
        val keyId = if (pubDer != null) EvidenceSigner.keyIdFor(pubDer) else "unknown"
        val evidence = EvidenceSigner.sign(signature, message, keyState.algorithm, keyId)

        val zktagHashPrefix = java.security.MessageDigest.getInstance("SHA-256").digest(zktag.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        var report = baseReport +
            "\nmint: OK\n" +
            "device_key_algorithm: ${keyState.algorithm} (${keyState.securityLevel}, sig_alg=${keyState.signatureAlgorithm})\n" +
            "device_key_tradeoff: ${keyState.tradeoffNote}\n" +
            "evidence_type: ${evidence.type}/${evidence.version} key_id=${evidence.keyId}\n" +
            "zktag_sha256_prefix (value-free, never the raw zktag): $zktagHashPrefix\n" +
            "scope_domain: $scopeDomain\n"

        if (handoff != null) {
            try {
                val presentation = HandoffClient.buildPresentation("B", claim, challenge, zktag, listOf(evidence))
                val responseUri = challenge.optString("response_uri", "").ifEmpty { null }
                if (responseUri == null) {
                    Log.w(TAG, "M2 stage: handoff challenge carries no response_uri — cannot direct_post")
                    report += "handoff: FAILED — challenge carries no response_uri\n"
                } else {
                    val state = if (challenge.has("state")) challenge.getString("state") else null
                    Log.i(TAG, "M2 stage: handoff direct_post -> $responseUri (state_present=${state != null})")
                    val result = HandoffClient.postDirectPost(responseUri, state, presentation)
                    Log.i(TAG, "M2 stage: handoff direct_post response http_status=${result.httpStatus} body=${result.body}")
                    report += "handoff: direct_post http_status=${result.httpStatus} -> ${result.body}\n"
                }
            } catch (e: Exception) {
                Log.e(TAG, "M2 stage: handoff direct_post FAILED", e)
                report += "handoff: FAILED ${e.javaClass.simpleName}: ${e.message}\n"
            }
            pendingHandoff = null
        }
        report += "\nverdict: PASS (minted)"
        emitReport(report)
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
            val first = DeviceKey.ensureKey(applicationContext)
            val ms0 = (System.nanoTime() - t0) / 1_000_000
            log.append("call_1 (generate-or-reuse): algorithm=${first.algorithm} level=${first.securityLevel} auth_mode=${first.authMode} winner_row=${first.winnerRowId} (${ms0}ms)\n")
            for (a in first.matrix) log.append("  matrix ${a.rowId}: ${if (a.ok) "OK level=${a.actualSecurityLevel}" else "FAILED ${a.exception}"}\n")

            val sig1 = DeviceKey.initSignature(first)
            log.append("call_1 initSignature: ${if (sig1 != null) "OK (non-null Signature)" else "FAILED (null)"}\n")

            val t1 = System.nanoTime()
            val second = DeviceKey.ensureKey(applicationContext)
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
