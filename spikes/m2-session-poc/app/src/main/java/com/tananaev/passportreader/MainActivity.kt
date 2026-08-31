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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
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
import java.io.ByteArrayInputStream
import java.security.Signature
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * PRD v1.17 §6.2 item 12 riskiest-assumption POC for the M2 rewrite: compose
 * StrongBox key generation + biometric prompt + PACE chip read in ONE
 * foreground-dispatch NFC session. PASS = the IsoDep session survives the
 * biometric UI interruption and the chip read completes in the same session.
 *
 * Forked from spikes/m2-scan (docs/logs/M2-SCAN-EVIDENCE.md). UNCHANGED reuse
 * from that fork: [M0Probe.passiveAuth] / [M0Probe.loadMasterList] and the
 * PACE-then-BAC access-establishment shape (same code as m2-scan's
 * `ReadTask.doInBackground` up through the DG1/SOD read).
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * ---------------------------------------------------------------------
 * §6.2 items 1, 4, 5, 6 — what changed from m2-scan, and why:
 * ---------------------------------------------------------------------
 *  - Item 1: [SessionKey] generates (first run) or reuses (later runs) an
 *    Android Keystore signing key, user-auth-bound, per-use where the
 *    platform allows it. It is a completely separate code path from
 *    [M0Probe.deriveCandidates] — this file never feeds Keystore material
 *    into zktag derivation, and in fact never calls deriveCandidates at all
 *    (item 4's "mode is irrelevant here" — no zktag derivation in this POC).
 *  - Item 4: no mode control anywhere (removes the F5 mode-radio surface
 *    entirely rather than trying to root-cause or restructure it).
 *  - Item 5: `ResultActivity` is deleted, not deprioritized. The report is
 *    rendered in-place in `report_view` (activity_main.xml) and is
 *    value-free by construction: field names, counts, hashes, verdicts and
 *    timings only, matching m2-scan's logging discipline. No screen in this
 *    app ever holds a DG1/MRZ/personal field.
 *  - Item 6: MRZ fields are wiped in onStop() (never onPause() — NFC
 *    foreground dispatch pauses/resumes this still-visible activity before
 *    onNewIntent runs) and are KEPT on an access-establishment failure
 *    (PACE/BAC SW 0x6300->0x6985) so a mistyped key is a retry, not a full
 *    retype; wiped on any other outcome (success or a later-stage failure).
 */
abstract class MainActivity : AppCompatActivity() {

    private lateinit var passportNumberView: EditText
    private lateinit var expirationDateView: EditText
    private lateinit var birthDateView: EditText
    private lateinit var mainLayout: View
    private lateinit var loadingLayout: View
    private lateinit var reportView: TextView

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
        mainLayout = findViewById(R.id.main_layout)
        loadingLayout = findViewById(R.id.loading_layout)
        reportView = findViewById(R.id.report_view)

        // NB: no SharedPreferences read/write anywhere in this activity, same
        // discipline as m2-scan — the three MRZ fields start empty every
        // launch and are typed by hand every run.

        findViewById<View>(R.id.button_m2_masterlist_probe).setOnClickListener {
            Thread { M2MasterlistProbe.runAndReport(applicationContext) }.start()
        }

        expirationDateView.setOnClickListener {
            val c = loadDate(expirationDateView)
            val dialog = DatePickerDialog.newInstance(
                { _, year, monthOfYear, dayOfMonth ->
                    val value = String.format(Locale.US, "%d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                    expirationDateView.setText(value)
                },
                c[Calendar.YEAR],
                c[Calendar.MONTH],
                c[Calendar.DAY_OF_MONTH],
            )
            dialog.showYearPickerFirst(true)
            supportFragmentManager.beginTransaction().add(dialog, null).commit()
        }

        birthDateView.setOnClickListener {
            val c = loadDate(birthDateView)
            val dialog = DatePickerDialog.newInstance(
                { _, year, monthOfYear, dayOfMonth ->
                    val value = String.format(Locale.US, "%d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                    birthDateView.setText(value)
                },
                c[Calendar.YEAR],
                c[Calendar.MONTH],
                c[Calendar.DAY_OF_MONTH],
            )
            dialog.showYearPickerFirst(true)
            supportFragmentManager.beginTransaction().add(dialog, null).commit()
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter != null) {
            val intent = Intent(applicationContext, this.javaClass)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            val filter = arrayOf(arrayOf("android.nfc.tech.IsoDep"))
            adapter.enableForegroundDispatch(this, pendingIntent, null, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        adapter?.disableForegroundDispatch(this)
        // NOTE: no wipeMrz() here — see class doc / §6.2 item 6 / m2-scan F2.
    }

    override fun onStop() {
        super.onStop()
        wipeMrz()
    }

    private fun wipeMrz() {
        passportNumberView.text?.clear()
        expirationDateView.text?.clear()
        birthDateView.text?.clear()
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: Tag? = intent.extras?.getParcelable(NfcAdapter.EXTRA_TAG)
            if (tag?.techList?.contains("android.nfc.tech.IsoDep") == true) {
                // Coordinator correction 2026-08-31, BUG 3 (minor, guard-only): a
                // stale/re-delivered ACTION_TECH_DISCOVERED intent can land after
                // wipeMrz() has already cleared the fields (e.g. the previous tag
                // lingering near the antenna after a run finished). Previously
                // this fell through to convertDate("") on every field, which
                // logs a caught-but-noisy ParseException per field and then
                // shows the "please provide details" snackbar for an intent the
                // user never intended as a new attempt. Drop it silently instead
                // — a genuine new tap always has freshly-typed, non-empty fields.
                val passportRaw = passportNumberView.text?.toString()
                val expirationRaw = expirationDateView.text?.toString()
                val birthRaw = birthDateView.text?.toString()
                if (passportRaw.isNullOrEmpty() || expirationRaw.isNullOrEmpty() || birthRaw.isNullOrEmpty()) {
                    Log.i(TAG, "M2 stage: ignoring tag intent — MRZ fields are empty (stale/re-delivered intent, not a new attempt)")
                    return
                }
                val expirationDate = convertDate(expirationRaw)
                val birthDate = convertDate(birthRaw)
                if (expirationDate != null && birthDate != null) {
                    val bacKey: BACKeySpec = BACKey(passportRaw, birthDate, expirationDate)
                    startSession(IsoDep.get(tag), bacKey)
                } else {
                    Snackbar.make(passportNumberView, R.string.error_input, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * §6.2 item 12 — the composed session. Step order matches the owner's
     * decision verbatim: tag discovered -> IsoDep.connect() (recorded here,
     * on the main thread, so its own PASS/FAIL is known before anything
     * else happens) -> device key ready (generated at first run, reused
     * after) -> BiometricPrompt shown immediately -> on success, PACE (BAC
     * fallback) + DG1/SOD read + passiveAuth + Keystore signature, all
     * against the SAME already-connected IsoDep instance — it is never
     * reconnected, so a lost session across the biometric UI interruption
     * is directly observable rather than papered over by a silent retry.
     */
    private fun startSession(isoDep: IsoDep, bacKey: BACKeySpec) {
        mainLayout.visibility = View.GONE
        loadingLayout.visibility = View.VISIBLE
        val session = SessionReport()
        session.timeline.mark("tag_discovered")

        isoDep.timeout = 10000
        val connectOk = try {
            isoDep.connect()
            true
        } catch (e: Exception) {
            session.failureStep = "isodep_connect"
            session.failureMode = "${e.javaClass.simpleName}: ${e.message}"
            false
        }
        session.timeline.mark("isodep_connect_returned")
        session.connectIsConnected = isoDep.isConnected

        if (!connectOk || !isoDep.isConnected) {
            session.failureStep = session.failureStep ?: "isodep_connect"
            session.failureMode = session.failureMode ?: "connect() returned but isConnected()==false"
            finishSession(session)
            return
        }

        // Device key: generated at first run, reused after (§6.2 item 1).
        // Off the main thread — StrongBox key generation can take real time
        // and this is a POC, not a latency-tuned app.
        Thread {
            val keyState = SessionKey.ensureKey(applicationContext)
            runOnUiThread { continueSessionAfterKey(isoDep, bacKey, session, keyState) }
        }.start()
    }

    private fun continueSessionAfterKey(
        isoDep: IsoDep,
        bacKey: BACKeySpec,
        session: SessionReport,
        keyState: SessionKey.KeyState,
    ) {
        session.keyState = keyState
        SessionKey.noteKeyState(keyState)
        session.timeline.mark("key_ready")
        session.isConnectedAfterKey = isoDep.isConnected

        val sig = SessionKey.initSignature()
        if (sig == null) {
            session.failureStep = "signature_init"
            session.failureMode = "no usable signing key (see key matrix)"
            finishSession(session)
            return
        }
        // Coordinator correction 2026-08-31 (BUG B): captured NOW, before the
        // prompt, because the post-auth signing path depends on it — a
        // per-use key's CryptoObject returns an already-authorized Signature
        // after auth; a validity-window key's does NOT (its pre-prompt
        // initSign() only ever threw UserNotAuthenticatedException) and must
        // be re-initSign()'d against the SAME provider after auth succeeds.
        val perUseMode = SessionKey.isPerUseMode()
        session.signAuthPath = if (perUseMode) {
            "PER_USE (pre-prompt initSign; will use CryptoObject's own returned Signature post-auth)"
        } else {
            "WINDOW (pre-prompt initSign was pending-auth; will re-initSign() post-auth, same provider)"
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    session.timeline.mark("biometric_succeeded")
                    session.biometricResult = "SUCCESS"
                    // The core §6.2 item 12 measurement: did the IsoDep session
                    // survive the biometric UI interruption?
                    session.isConnectedAfterBiometric = isoDep.isConnected
                    // BUG B: never sign on a Signature whose initSign() threw.
                    // PER_USE: the framework hands back the SAME Signature
                    // object it authorized via the CryptoObject — use it
                    // directly. WINDOW: the pre-prompt Signature only ever
                    // reached PENDING_AUTH (initSign() threw
                    // UserNotAuthenticatedException) — re-initSign() now,
                    // inside the just-opened auth window, on a fresh
                    // resolveByAttempt() pass; this time it should succeed
                    // outright instead of throwing.
                    val signingSignature = if (perUseMode) {
                        result.cryptoObject?.signature
                    } else {
                        session.timeline.mark("window_mode_post_auth_reinit")
                        SessionKey.initSignature()
                    }
                    ReadTask(isoDep, bacKey, session, signingSignature).execute()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    session.timeline.mark("biometric_error")
                    session.biometricResult = "ERROR($errorCode): $errString"
                    session.isConnectedAfterBiometric = isoDep.isConnected
                    session.failureStep = "biometric_prompt"
                    session.failureMode = "biometric error $errorCode: $errString"
                    finishSession(session)
                }

                override fun onAuthenticationFailed() {
                    // One failed match attempt; the prompt stays open. Not a
                    // session-ending event.
                    Log.i(TAG, "M2 stage: biometric match failed once, prompt remains open")
                }
            },
        )
        session.timeline.mark("biometric_prompt_shown")
        try {
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(sig))
        } catch (e: Exception) {
            session.failureStep = "biometric_prompt_launch"
            session.failureMode = "${e.javaClass.simpleName}: ${e.message}"
            finishSession(session)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ReadTask(
        private val isoDep: IsoDep,
        private val bacKey: BACKeySpec,
        private val session: SessionReport,
        private val authorizedSignature: Signature?,
    ) : AsyncTask<Void?, Void?, Exception?>() {

        private lateinit var dg1File: DG1File
        private lateinit var sodFile: SODFile

        override fun doInBackground(vararg params: Void?): Exception? {
            try {
                if (!isoDep.isConnected) {
                    // The session did NOT survive the biometric UI interruption
                    // intact — this is itself the item 12 finding, recorded
                    // (not silently papered over) before attempting recovery.
                    Log.w(TAG, "M2 stage: IsoDep NOT connected entering chip read")
                    isoDep.connect()
                    Log.i(TAG, "M2 stage: reconnect after loss succeeded (session was NOT held across biometric UI)")
                    session.reconnectedAfterLoss = true
                }

                val cardService = CardService.getInstance(isoDep)
                cardService.open()
                val service = PassportService(
                    cardService,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false,
                    false,
                )
                service.open()
                Log.i(TAG, "M2 stage: passport applet service opened")

                var paceSucceeded = false
                try {
                    val cardAccessFile = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
                    for (securityInfo: SecurityInfo in cardAccessFile.securityInfos) {
                        if (securityInfo is PACEInfo) {
                            service.doPACE(
                                bacKey,
                                securityInfo.objectIdentifier,
                                PACEInfo.toParameterSpec(securityInfo.parameterId),
                                null,
                            )
                            paceSucceeded = true
                        }
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "M2 stage: PACE unavailable (${e.javaClass.simpleName}: ${e.message})")
                }
                Log.i(TAG, "M2 stage: PACE ${if (paceSucceeded) "SUCCEEDED" else "not used"}")

                try {
                    service.sendSelectApplet(paceSucceeded)
                    Log.i(TAG, "M2 stage: applet selected")
                    if (!paceSucceeded) {
                        try {
                            service.getInputStream(PassportService.EF_COM).read()
                            Log.i(TAG, "M2 stage: EF.COM readable without BAC")
                        } catch (e: Exception) {
                            Log.i(TAG, "M2 stage: attempting BAC")
                            service.doBAC(bacKey)
                            Log.i(TAG, "M2 stage: BAC SUCCEEDED")
                        }
                    }
                } catch (e: Exception) {
                    // §6.2 item 6: this is specifically an access-establishment
                    // failure (SW 0x6300->0x6985 shape, or PACE/BAC key
                    // mismatch) — MRZ is kept, not wiped, for finishSession.
                    session.failureStep = "access_establishment"
                    session.failureMode = "${e.javaClass.simpleName}: ${e.message}"
                    throw e
                }
                session.accessProtocol = if (paceSucceeded) "PACE" else "BAC"
                session.timeline.mark("access_established (${session.accessProtocol})")
                session.isConnectedAfterAccess = isoDep.isConnected

                val dg1Encoded = IOUtils.toByteArray(service.getInputStream(PassportService.EF_DG1))
                dg1File = DG1File(ByteArrayInputStream(dg1Encoded))
                val sodEncoded = IOUtils.toByteArray(service.getInputStream(PassportService.EF_SOD))
                sodFile = SODFile(ByteArrayInputStream(sodEncoded))
                session.dg1SodRead = true
                Log.i(TAG, "M2 stage: DG1 + SOD read")
                session.timeline.mark("dg1_and_sod_read")
                session.isConnectedAfterRead = isoDep.isConnected

                // UNCHANGED reuse of M0Probe (see class doc): masterlist load +
                // passive auth, same code path M0/m2-scan measured.
                val master = M0Probe.loadMasterList(assets.open("masterList"))
                val verdict = M0Probe.passiveAuth(dg1File, sodFile, master)
                session.passiveAuthVerdict = verdict.toString()
                Log.i(TAG, "M2 stage: passive_auth $verdict")
                session.timeline.mark("passive_auth_verified")

                // Item 12: sign in the SAME session, after the chip read, using
                // the Signature authorized by the biometric prompt earlier in
                // this same session. NOT the D30 payload layout (§6.2 item 9) —
                // a fixed test message only, see SessionKey.TEST_MESSAGE.
                if (authorizedSignature != null) {
                    session.signResult = try {
                        val (hex, rawSig) = SessionKey.signTestMessage(authorizedSignature)
                        // Coordinator instruction 2026-08-31: write the raw
                        // signature + DER pubkey to the app's PRIVATE files dir
                        // only — never the on-screen report, never logcat as
                        // content — so "sign OK" can be verified off-device
                        // with openssl instead of taken on this app's word.
                        // These are device-attester-key artifacts (this app's
                        // own Keystore key), NOT document/PII data.
                        try {
                            java.io.File(applicationContext.filesDir, "sig-latest.bin").writeBytes(rawSig)
                            SessionKey.currentPublicKeyDer()?.let { der ->
                                java.io.File(applicationContext.filesDir, "pubkey-latest.der").writeBytes(der)
                            }
                            Log.i(TAG, "M2 stage: sign artifacts written to app files dir (sig-latest.bin, pubkey-latest.der) — pull via adb; no content logged")
                        } catch (fileEx: Exception) {
                            Log.w(TAG, "M2 stage: failed writing sign artifacts to files dir: ${fileEx.javaClass.simpleName}: ${fileEx.message}")
                        }
                        "OK sha256(signature)=$hex"
                    } catch (e: Exception) {
                        "FAILED ${e.javaClass.simpleName}: ${e.message}"
                    }
                } else {
                    session.signResult = "SKIPPED (no CryptoObject signature from biometric result)"
                }
                // Coordinator correction 2026-08-31 (BUG A): a failed/skipped
                // sign step is a real session failure and MUST be reflected in
                // failure_step — run 3's report said "verdict: PASS" with
                // failure_step "none" while sign_result said FAILED, which is
                // a self-contradicting report. Never again: this is the single
                // place sign outcome is recorded, and it sets failureStep
                // right here rather than relying on a later re-derivation.
                if (!session.signResult!!.startsWith("OK") && session.failureStep == null) {
                    session.failureStep = "sign"
                    session.failureMode = session.signResult
                }
                Log.i(TAG, "M2 stage: sign ${session.signResult}")
                session.timeline.mark("signed")
                session.isConnectedAfterSign = isoDep.isConnected
            } catch (e: Exception) {
                Log.e(TAG, "M2 SESSION FAILED — see the last 'M2 stage' line above for how far it got", e)
                if (session.failureStep == null) {
                    session.failureStep = if (!session.dg1SodRead) "dg1_sod_read" else "post_read"
                    session.failureMode = "${e.javaClass.simpleName}: ${e.message}"
                }
                return e
            }
            return null
        }

        override fun onPostExecute(result: Exception?) {
            try {
                isoDep.close()
            } catch (e: Exception) {
                Log.w(TAG, "isoDep.close() failed: ${e.javaClass.simpleName}")
            }
            // BUG A: an exception-free AsyncTask result is necessary but NOT
            // sufficient for PASS — see SessionReport.allStepsOk(), the single
            // source of truth finishSession()/buildReport() both read from,
            // so the report can never again say PASS while a step FAILED.
            if (result != null && session.failureStep == null) {
                session.failureStep = if (!session.dg1SodRead) "dg1_sod_read" else "post_read"
                session.failureMode = "${result.javaClass.simpleName}: ${result.message}"
            }
            finishSession(session)
        }
    }

    /**
     * §6.2 item 6: MRZ is kept (not wiped) ONLY on an access-establishment
     * failure, so a mistyped key is a retry, not a full retype. Every other
     * outcome (success, or a failure past access establishment) wipes it.
     */
    private fun finishSession(session: SessionReport) {
        mainLayout.visibility = View.VISIBLE
        loadingLayout.visibility = View.GONE

        val keepMrz = session.failureStep == "access_establishment"
        if (keepMrz) {
            Snackbar.make(passportNumberView, R.string.error_read, Snackbar.LENGTH_LONG).show()
        } else {
            wipeMrz()
        }

        val report = buildReport(session)
        reportView.text = report
        Log.i(TAG, "\n===== M2 SESSION-POC REPORT (value-free) =====\n$report\n===== END =====")
    }

    /** Value-free report text (§6.2 item 5) — field names, counts, hashes,
     * verdicts and timings only, safe to render and safe to screenshot.
     *
     * Coordinator correction 2026-08-31 (BUG A): [SessionReport.allStepsOk]
     * is the ONLY place "PASS" is decided — computed from every recorded
     * step, not from whether the background task merely finished without
     * throwing. A step marked FAILED anywhere in [session] can never coexist
     * with a PASS verdict again.
     */
    private fun buildReport(session: SessionReport): String {
        val sessionSucceeded = session.allStepsOk()
        val sb = StringBuilder()
        sb.append("verdict: ${if (sessionSucceeded) "PASS" else "FAIL"}\n")
        sb.append("failure_step: ${session.failureStep ?: "none"}\n")
        sb.append("failure_mode: ${session.failureMode ?: "none"}\n\n")

        sb.append("-- IsoDep session --\n")
        sb.append("connect_ok: ${session.connectIsConnected}\n")
        sb.append("isConnected_after_key: ${session.isConnectedAfterKey}\n")
        sb.append("isConnected_after_biometric: ${session.isConnectedAfterBiometric}\n")
        sb.append("reconnected_after_loss: ${session.reconnectedAfterLoss}\n")
        sb.append("isConnected_after_access: ${session.isConnectedAfterAccess}\n")
        sb.append("isConnected_after_read: ${session.isConnectedAfterRead}\n")
        sb.append("isConnected_after_sign: ${session.isConnectedAfterSign}\n\n")

        sb.append("-- device key (§6.2 item 1) --\n")
        val ks = session.keyState
        if (ks == null) {
            sb.append("key_state: not reached\n")
        } else if (ks.reusedExisting) {
            sb.append("key_state: REUSED existing key (generated on a prior run)\n")
            sb.append("auth_mode (read back from KeyInfo, never assumed): ${ks.authMode}\n")
            sb.append("signature_algorithm: ${ks.signatureAlgorithm}\n")
            sb.append("security_level: ${ks.securityLevel}\n")
            sb.append("inside_secure_hardware: ${ks.insideSecureHardware}\n")
            sb.append("strongbox_keystore_feature_present: ${ks.strongBoxFeaturePresent}\n")
        } else {
            sb.append("key_state: GENERATED this run\n")
            sb.append("key_algorithm_matrix (a1/a2/b1/b2/c/d, full matrix always reported, a2/b2 diagnostic-only):\n")
            for (a in ks.attempts) {
                val resultText = if (a.ok) {
                    "OK level=${a.actualSecurityLevel} inside_secure_hardware=${a.insideSecureHardware} " +
                        "kpgProvider=${a.kpgProviderName} containsAliasAfterGen=${a.containsAliasAfterGen} " +
                        "pubkeyAlg=${a.publicKeyAlgorithm} pubkeyEncodedLen=${a.publicKeyEncodedLength} " +
                        "confirmedAndroidKeyStoreKey=${a.confirmedAndroidKeyStoreKey}" +
                        if (a.softwareOrTeeFallbackSuspected) {
                            " [SUSPECTED FALLBACK — StrongBox requested but level=${a.actualSecurityLevel}, NOT a clean StrongBox OK]"
                        } else ""
                } else {
                    "FAILED (${a.exception})"
                }
                sb.append("  ${a.rowId} ${a.label}: $resultText\n")
            }
            val winnerAttempt = ks.attempts.firstOrNull { it.rowId == ks.winnerRowId }
            sb.append("winner: ${winnerAttempt?.let { "${it.rowId} ${it.label}" } ?: "NONE — a1/b1/c/d all failed"}\n")
            sb.append("signature_algorithm: ${ks.signatureAlgorithm}\n")
            sb.append("auth_mode (read back from KeyInfo, never assumed): ${ks.authMode}\n")
            sb.append(
                "per_use_auth_binding requested (at key generation): ${ks.perUseAuth} (false = the builder fell " +
                    "back to a 15s validity window at request time); auth_mode above is the ACTUAL readback and " +
                    "is authoritative; current at-sign-time mode: ${SessionKey.currentAuthModeLabel()} " +
                    "(may differ from generation time if initSign() was rejected on every provider and the " +
                    "runtime fallback regenerated the key as a validity window)\n",
            )
            sb.append("security_level: ${ks.securityLevel}\n")
            sb.append("inside_secure_hardware: ${ks.insideSecureHardware}\n")
            sb.append("strongbox_keystore_feature_present: ${ks.strongBoxFeaturePresent}\n")
            val a1 = ks.attempts.firstOrNull { it.rowId == "a1" }
            val a2 = ks.attempts.firstOrNull { it.rowId == "a2" }
            if (a1?.ok == false) {
                // Coordinator correction 2026-08-31: verify a2's StrongBox claim
                // independently before concluding the escalation is resolved —
                // level==STRONGBOX alone is not enough; require the full
                // confirmedAndroidKeyStoreKey chain (real AndroidKeyStore
                // provider, alias really present, KeyInfo readback clean).
                val a2CleanStrongBoxLevel = a2?.ok == true && !a2.softwareOrTeeFallbackSuspected
                val a2FullyConfirmed = a2?.ok == true && a2.confirmedAndroidKeyStoreKey && a2CleanStrongBoxLevel
                sb.append(
                    "ESCALATION: a1 (Ed25519 via EC curve, StrongBox) FAILED (${a1.exception})" +
                        when {
                            a2FullyConfirmed ->
                                " [RESOLVED — a2 (literal \"Ed25519\" entry point) is a VERIFIED genuine AndroidKeyStore " +
                                    "StrongBox Ed25519 key: kpgProvider=${a2?.kpgProviderName}, " +
                                    "containsAliasAfterGen=${a2?.containsAliasAfterGen}, pubkeyAlg=${a2?.publicKeyAlgorithm}, " +
                                    "pubkeyEncodedLen=${a2?.publicKeyEncodedLength} bytes, level=${a2?.actualSecurityLevel}. " +
                                    "Conclusion: Ed25519-in-StrongBox IS available on this device — this is a PROVIDER " +
                                    "ENTRY-POINT quirk (the documented EC-curve-alias path a1 uses is rejected by " +
                                    "AndroidKeyStoreKeyPairGeneratorSpi.checkValidKeySize with \"Unsupported StrongBox EC: " +
                                    "ed25519\", but the literal algorithm-name path a2 uses is accepted), NOT a hardware gap. " +
                                    "Winner preference order (a1->b1->c->d) intentionally left unchanged pending an " +
                                    "explicit owner decision on whether to add/prefer this entry point."
                            a2CleanStrongBoxLevel ->
                                " [a2 literal-\"Ed25519\" entry point reported OK at StrongBox level but is NOT fully " +
                                    "verified (confirmedAndroidKeyStoreKey=false — see the a2 row above for which check " +
                                    "failed) — UNCONFIRMED, do not yet treat this as resolving the escalation]"
                            a2?.ok == true ->
                                " [a2 literal-\"Ed25519\" entry point \"succeeded\" but only at level=${a2.actualSecurityLevel}, " +
                                    "NOT StrongBox — SUSPECTED SOFTWARE/TEE FALLBACK, not real evidence StrongBox supports " +
                                    "Ed25519 here]"
                            else -> ""
                        } +
                        " — conflicts with §6.2 item 1 / D30's sig-ed25519/1 assumption; " +
                        "used ${winnerAttempt?.let { "${it.rowId} ${it.label}" }} instead (winner preference order " +
                        "unchanged: a1->b1->c->d — a2/b2 remain diagnostic-only per standing design)\n",
                )
            }
        }
        sb.append("\n")

        sb.append("-- biometric --\n")
        sb.append("biometric_result: ${session.biometricResult}\n\n")

        sb.append("-- chip read --\n")
        sb.append("access_protocol: ${session.accessProtocol}\n")
        sb.append("dg1_sod_read: ${session.dg1SodRead}\n")
        sb.append("passive_auth: ${session.passiveAuthVerdict ?: "not reached"}\n")
        sb.append("sign_auth_path: ${session.signAuthPath ?: "not reached"}\n")
        sb.append("sign_result: ${session.signResult ?: "not reached"}\n")
        val providerTrace = SessionKey.providerTraceLines()
        if (providerTrace.isNotEmpty()) {
            sb.append("signature_provider_attempts (one line per JCA provider tried, in priority order):\n")
            sb.append(providerTrace.joinToString("\n")).append("\n")
        }
        sb.append("\n")

        sb.append("-- timings --\n")
        sb.append(session.timeline.report().joinToString("\n"))
        return sb.toString()
    }

    /** All the per-tap state for one §6.2 item 12 session. Never carries a
     * chip-derived value — only booleans, labels, hashes and timings. */
    private class SessionReport {
        val timeline = M0Probe.Timeline()
        var connectIsConnected: Boolean = false
        var keyState: SessionKey.KeyState? = null
        var isConnectedAfterKey: Boolean? = null
        var biometricResult: String = "not shown"
        var isConnectedAfterBiometric: Boolean? = null
        var reconnectedAfterLoss: Boolean = false
        var accessProtocol: String = "unknown"
        var isConnectedAfterAccess: Boolean? = null
        var dg1SodRead: Boolean = false
        var isConnectedAfterRead: Boolean? = null
        var passiveAuthVerdict: String? = null
        var signAuthPath: String? = null
        var signResult: String? = null
        var isConnectedAfterSign: Boolean? = null
        var failureStep: String? = null
        var failureMode: String? = null

        /**
         * Coordinator correction 2026-08-31 (BUG A) — the ONLY definition of
         * overall PASS/FAIL, used by both [MainActivity.finishSession] (MRZ
         * wipe decision already used [failureStep] directly, unaffected) and
         * [MainActivity.buildReport] (the verdict line). PASS requires EVERY
         * step to have actually succeeded — session survival through
         * connect/key/access/read, passive-auth having actually RUN (ok, not
         * necessarily allowed — a real "no" is not a POC failure, see
         * M0Probe.Verdict), AND the sign step having actually produced a
         * signature. No exception having been thrown is necessary but not
         * sufficient — run 3 proved that (sign failed, no exception thrown,
         * verdict wrongly said PASS).
         */
        fun allStepsOk(): Boolean {
            if (failureStep != null) return false
            if (!connectIsConnected) return false
            if (!dg1SodRead) return false
            val pa = passiveAuthVerdict ?: return false
            if (!pa.contains("ok=true")) return false
            val sr = signResult ?: return false
            if (!sr.startsWith("OK")) return false
            return true
        }
    }

    private fun convertDate(input: String?): String? {
        if (input == null) {
            return null
        }
        return try {
            SimpleDateFormat("yyMMdd", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input)!!)
        } catch (e: ParseException) {
            Log.w(MainActivity::class.java.simpleName, e)
            null
        }
    }

    private fun loadDate(editText: EditText): Calendar {
        val calendar = Calendar.getInstance()
        if (editText.text.isNotEmpty()) {
            try {
                calendar.timeInMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(editText.text.toString())!!.time
            } catch (e: ParseException) {
                Log.w(MainActivity::class.java.simpleName, e)
            }
        }
        return calendar
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
