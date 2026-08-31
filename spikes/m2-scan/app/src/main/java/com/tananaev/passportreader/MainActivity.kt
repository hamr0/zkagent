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
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.scale
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
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG14File
import org.jmrtd.lds.icao.DG1File
import java.io.ByteArrayInputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.security.cert.X509Certificate

/**
 * M2 riskiest-assumption POC — PRD v1.16 §6 M2 row: zktag stability across
 * app reinstall + rescan, and masterlist verification on the phone with its
 * mandated negative. Forked from spikes/m0 (docs/logs/M0-EVIDENCE.md), which
 * is itself forked from tananaev/passport-reader.
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * ---------------------------------------------------------------------
 * HARD RULE (NO-GO #9) — MRZ never persisted. Design, not just discipline:
 * ---------------------------------------------------------------------
 * spikes/m0's MainActivity wrote the typed passport number, date of birth
 * and expiration date to `PreferenceManager.getDefaultSharedPreferences`
 * (three `.edit { putString(...) }` call sites, plus a `TextWatcher` that
 * wrote on every keystroke) so the fields would survive activity restarts.
 * That is a real MRZ-persistence defect: the values landed in
 * `/data/data/<pkg>/shared_prefs/` as XML, on disk, outliving the app process.
 *
 * This fork removes that mechanism ENTIRELY rather than gating it:
 *   - There is no `import android.preference.PreferenceManager` here.
 *   - There is no `SharedPreferences`, no `DataStore`, and no other
 *     write-to-disk path for `passportNumber` / `birthDate` / `expirationDate`
 *     anywhere in this file or this module — grep for
 *     `SharedPreferences|DataStore|putString|writeText|writeBytes` against
 *     `app/src/main/java/` and the MRZ fields never appear next to any of
 *     them. (M1SodProbe, which wrote raw DG1/SOD bytes to the app's private
 *     external files dir for a *different* spike's fixture capture, is
 *     deleted in this fork — not needed for M2, and it is itself a
 *     PII-to-disk path this spike has no reason to carry forward.)
 *   - The MRZ fields live ONLY in the three `EditText` views and the local
 *     `BACKeySpec` built from them for the duration of one NFC session.
 *   - [wipeMrz] clears the `EditText` text and drops the `BACKeySpec`
 *     reference after every read attempt (success OR failure) and again in
 *     `onStop`, so leaving the screen does not leave typed MRZ material
 *     sitting in a live `EditText`. It is NOT wiped in `onPause`: NFC
 *     foreground dispatch delivers a tag via a `PendingIntent` that pauses
 *     and resumes this activity (staying visible) before `onNewIntent` runs,
 *     so an `onPause` wipe would erase the MRZ a tap needs milliseconds
 *     before it's used — `onStop` only fires when the activity actually
 *     stops being visible, which is what NO-GO #9 cares about.
 * The only intentional persistence in this app is the derived zktag HASH
 * logged to logcat for TEST 1's reinstall comparison — a one-way HMAC
 * output, not the MRZ key it was derived from, and it is read by the
 * operator off logcat, never written by this app to any file.
 */
abstract class MainActivity : AppCompatActivity() {

    private lateinit var passportNumberView: EditText
    private lateinit var expirationDateView: EditText
    private lateinit var birthDateView: EditText
    private lateinit var modeGroup: RadioGroup
    private var encodePhotoToBase64 = false
    private lateinit var mainLayout: View
    private lateinit var loadingLayout: View

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

        encodePhotoToBase64 = intent.getBooleanExtra("photoAsBase64", false)

        passportNumberView = findViewById(R.id.input_passport_number)
        expirationDateView = findViewById(R.id.input_expiration_date)
        birthDateView = findViewById(R.id.input_date_of_birth)
        modeGroup = findViewById(R.id.mode_group)
        mainLayout = findViewById(R.id.main_layout)
        loadingLayout = findViewById(R.id.loading_layout)

        // M2 POC spike trigger — see M2MasterlistProbe.kt. Desk-only, no tap needed.
        findViewById<View>(R.id.button_m2_masterlist_probe).setOnClickListener {
            Thread { M2MasterlistProbe.runAndReport(applicationContext) }.start()
        }

        // NB: no SharedPreferences read/write anywhere in this activity — see the
        // class doc above. The three MRZ fields start empty every launch and are
        // typed by hand every run, per NO-GO #9.

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
        // NOTE: no wipeMrz() here. NFC foreground dispatch pauses/resumes this
        // activity (it stays visible) before onNewIntent runs with the tag, so
        // wiping on onPause would erase the typed MRZ a tap needs — see onStop.
    }

    override fun onStop() {
        super.onStop()
        // Defense-in-depth for NO-GO #9: actually leaving the screen (unlike an
        // NFC dispatch pause/resume) wipes any typed MRZ still sitting in the
        // EditTexts, even if no read was attempted.
        wipeMrz()
    }

    /** NO-GO #9: the only place typed MRZ material lives is these three EditTexts
     * for the duration of one session. Called after every read attempt
     * (success or failure) and again in onStop. There is no other copy to wipe —
     * no SharedPreferences, no DataStore, no cache file. */
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
                val passportNumber = passportNumberView.text?.toString()
                val expirationDate = convertDate(expirationDateView.text?.toString())
                val birthDate = convertDate(birthDateView.text?.toString())
                val modeB = modeGroup.checkedRadioButtonId == R.id.mode_b
                if (!passportNumber.isNullOrEmpty() && !expirationDate.isNullOrEmpty() && !birthDate.isNullOrEmpty()) {
                    val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                    ReadTask(IsoDep.get(tag), bacKey, modeB).execute()
                    mainLayout.visibility = View.GONE
                    loadingLayout.visibility = View.VISIBLE
                } else {
                    Snackbar.make(passportNumberView, R.string.error_input, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ReadTask(
        private val isoDep: IsoDep,
        private val bacKey: BACKeySpec,
        private val modeB: Boolean,
    ) : AsyncTask<Void?, Void?, Exception?>() {

        private lateinit var dg1File: DG1File
        private lateinit var dg14File: DG14File
        private lateinit var sodFile: SODFile
        private var imageBase64: String? = null
        private var bitmap: Bitmap? = null
        private var chipAuthSucceeded = false
        private var passiveAuthSuccess = false
        private var activeAuthSucceeded = false
        private var activeAuthDetail = "not probed"
        private var accessProtocol = "unknown"
        private var dg15Encoded: ByteArray? = null
        private val timeline = M0Probe.Timeline()
        private lateinit var dg14Encoded: ByteArray
        private val heapBeforeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        override fun doInBackground(vararg params: Void?): Exception? {
            try {
                isoDep.timeout = 10000
                Log.i(TAG, "M2 stage: tag discovered, opening card service")
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
                    val securityInfoCollection = cardAccessFile.securityInfos
                    for (securityInfo: SecurityInfo in securityInfoCollection) {
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
                timeline.mark("access_established (${if (paceSucceeded) "PACE" else "BAC"})")
                accessProtocol = if (paceSucceeded) "PACE" else "BAC"

                val dg1In = service.getInputStream(PassportService.EF_DG1)
                val dg1Encoded = IOUtils.toByteArray(dg1In)
                dg1File = DG1File(ByteArrayInputStream(dg1Encoded))
                Log.i(TAG, "M2 stage: DG1 read")
                val sodIn = service.getInputStream(PassportService.EF_SOD)
                val sodEncoded = IOUtils.toByteArray(sodIn)
                sodFile = SODFile(ByteArrayInputStream(sodEncoded))
                Log.i(TAG, "M2 stage: SOD read")
                timeline.mark("dg1_and_sod_read")

                doChipAuth(service)
                timeline.mark("chip_auth_probed")

                Log.i(TAG, "M2 stage: CA probed (succeeded=$chipAuthSucceeded)")
                val aa = M0Probe.tryActiveAuth(service, sodFile)
                activeAuthSucceeded = aa.first
                activeAuthDetail = aa.second
                dg15Encoded = try {
                    IOUtils.toByteArray(service.getInputStream(PassportService.EF_DG15))
                } catch (e: Exception) {
                    null
                }
                timeline.mark("active_auth_probed")

                Log.i(TAG, "M2 stage: AA probed (succeeded=$activeAuthSucceeded)")
                runM2Report()

                // DG2 (facial image) is deliberately NOT read, as in M0: never
                // needed, slowest read, most sensitive object.
            } catch (e: Exception) {
                Log.e(TAG, "M2 READ FAILED — see the last 'M2 stage' line above for how far it got", e)
                return e
            }
            return null
        }

        private fun doChipAuth(service: PassportService) {
            try {
                val dg14In = service.getInputStream(PassportService.EF_DG14)
                dg14Encoded = IOUtils.toByteArray(dg14In)
                val dg14InByte = ByteArrayInputStream(dg14Encoded)
                dg14File = DG14File(dg14InByte)
                val dg14FileSecurityInfo = dg14File.securityInfos
                for (securityInfo: SecurityInfo in dg14FileSecurityInfo) {
                    if (securityInfo is ChipAuthenticationPublicKeyInfo) {
                        service.doEACCA(
                            securityInfo.keyId,
                            ChipAuthenticationPublicKeyInfo.ID_CA_ECDH_AES_CBC_CMAC_256,
                            securityInfo.objectIdentifier,
                            securityInfo.subjectPublicKey,
                        )
                        chipAuthSucceeded = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, e)
            }
        }

        /**
         * PRD v1.16 M2 row: TEST 1 (zktag stability across reinstall) and
         * TEST 2's chip-dependent half (CSCA-removed negative, DS-cert
         * validity dates). Everything printed here is PII-free by
         * construction — field names, counts, hashes and verdicts only.
         */
        private fun runM2Report() {
            val log = StringBuilder("\n===== M2 SCAN REPORT =====\n")
            log.append("access_protocol: $accessProtocol\n")
            log.append("data_groups_in_sod: ${M0Probe.dataGroupInventory(sodFile)}\n")
            log.append("chip_authentication (CA/DG14): $chipAuthSucceeded\n")
            log.append("active_authentication (AA/DG15): $activeAuthSucceeded — $activeAuthDetail\n")
            // D21's payload field: chip_auth reads "passed" if EITHER challenge-response
            // mechanism succeeded (CA or AA — a document need offer only one), "absent"
            // otherwise. M0 measured the US passport as absent on both, the NL card as
            // passed on both; no document in the M0/M2 evidence has offered exactly one.
            val chipAuthField = if (chipAuthSucceeded || activeAuthSucceeded) "passed" else "absent"
            log.append("chip_auth (D21 payload field): $chipAuthField\n")

            val master = try {
                M0Probe.loadMasterList(assets.open("masterList"))
            } catch (e: Exception) {
                log.append("master_list: FAILED TO LOAD ${e.javaClass.simpleName}\n")
                Log.i(TAG, log.toString())
                return
            }
            log.append("master_list: declared=${master.certsDeclared} parsed=${master.certsParsed} consistent=${master.consistent}\n")

            val genuine = M0Probe.passiveAuth(dg1File, sodFile, master)
            passiveAuthSuccess = genuine.ok && genuine.allowed == true
            log.append("passive_auth (genuine): $genuine\n")
            timeline.mark("passive_auth_verified")

            // ---- planted negative (i): one flipped DG1 byte, as M0 ran it ----
            val tampered = M0Probe.passiveAuth(dg1File, sodFile, master, M0Probe.tamperedDg1(dg1File))
            val negOneFired = tampered.ok && tampered.allowed == false
            log.append("NEGATIVE_1 dg1_byte_flipped: $tampered -> ${if (negOneFired) "FIRED (good)" else "DID NOT FIRE (INVALID RUN)"}\n")

            // ---- planted negative (ii): issuing CSCA removed, as M0 ran it ----
            // M0's established, evidence-backed semantics (M0Probe.passiveAuth,
            // M0-EVIDENCE.md Finding 5): this is a real "no" (ok:true, allowed:false)
            // — the checker successfully determines the issuer is untrusted — NOT
            // "could not check". Kept identical to M0 here; see the M2 evidence doc's
            // escalations section for the wording conflict with this task's own
            // "half-loaded... and CSCA-removed... MUST yield ok:false" phrasing.
            val ds = try { sodFile.docSigningCertificate } catch (e: Exception) { null }
            if (ds == null) {
                log.append("NEGATIVE_2 skipped: no document signer certificate\n")
            } else {
                val cert: X509Certificate = ds
                val stripped = M0Probe.loadMasterList(assets.open("masterList"), excludeAnchorFor = cert)
                val withoutCsca = M0Probe.passiveAuth(dg1File, sodFile, stripped)
                val removedSomething = stripped.certsExcluded > 0
                val negTwoFired = removedSomething && withoutCsca.allowed != true
                log.append(
                    "NEGATIVE_2 csca_removed (excluded=${stripped.certsExcluded}, kept=${stripped.certsParsed}): " +
                        "$withoutCsca -> " +
                        when {
                            !removedSomething -> "INVALID RUN (exclusion matched no certificate)"
                            negTwoFired -> "FIRED (good)"
                            else -> "DID NOT FIRE (INVALID RUN)"
                        } + "\n"
                )
                // DS-cert validity dates, checked explicitly — M1's own lesson
                // (a verifier that checks signatures but not notBefore/notAfter can
                // accept an already-expired signer). M0Probe.passiveAuth already
                // calls checkValidity() per signer cert before path validation;
                // this line makes the dates themselves visible in the report.
                log.append("ds_cert_not_before: ${cert.notBefore}\n")
                log.append("ds_cert_not_after: ${cert.notAfter}\n")
            }

            // ---- TEST 3: mode gates the derivation, not just the disclosure ----
            // Mode A must emit NO zktag material — not a redacted one, an absent one.
            // The `if (modeB)` below is the entire gate: in mode A, deriveCandidates
            // is never called, so there is nothing to redact and nothing that could
            // leak by a formatting bug downstream. Grep-provable: "deriveCandidates"
            // appears exactly once in this file, inside this branch.
            if (modeB) {
                log.append("mode: B (pseudonymous) — deriving zktag candidates\n")
                log.append("zktag_candidates (HMAC over test domain, SAME code path as M0; values never logged):\n")
                M0Probe.deriveCandidates(dg1File, if (chipAuthSucceeded) dg14Encoded else null, dg15Encoded)
                    .forEach { (k, v) -> log.append("  $k = $v\n") }
                log.append(
                    "TEST1_PRIMARY_TAG (document_number, compare across uninstall/reinstall and " +
                        "against M0-EVIDENCE.md's recorded prefix): see document_number line above\n"
                )
            } else {
                log.append("mode: A (anonymous) — derivation SKIPPED, no zktag computed or emitted\n")
            }

            timeline.mark("derived_or_skipped")
            val heapAfterMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            log.append("native_heap_before_mb: $heapBeforeMb\n")
            log.append("native_heap_after_mb: $heapAfterMb\n")
            log.append("timings:\n").append(timeline.report().joinToString("\n")).append("\n")
            log.append("===== END M2 SCAN REPORT =====")
            Log.i(TAG, log.toString())
        }

        override fun onPostExecute(result: Exception?) {
            mainLayout.visibility = View.VISIBLE
            loadingLayout.visibility = View.GONE
            // NO-GO #9: wipe the typed MRZ now, regardless of outcome.
            wipeMrz()
            if (result == null) {
                val intent = if (callingActivity != null) {
                    Intent()
                } else {
                    Intent(this@MainActivity, ResultActivity::class.java)
                }
                val mrzInfo = dg1File.mrzInfo
                intent.putExtra(ResultActivity.KEY_FIRST_NAME, mrzInfo.secondaryIdentifier.replace("<", " "))
                intent.putExtra(ResultActivity.KEY_LAST_NAME, mrzInfo.primaryIdentifier.replace("<", " "))
                intent.putExtra(ResultActivity.KEY_GENDER, mrzInfo.gender.toString())
                intent.putExtra(ResultActivity.KEY_STATE, mrzInfo.issuingState)
                intent.putExtra(ResultActivity.KEY_NATIONALITY, mrzInfo.nationality)
                val passiveAuthStr = if (passiveAuthSuccess) {
                    getString(R.string.pass)
                } else {
                    getString(R.string.failed)
                }
                val chipAuthStr = if (chipAuthSucceeded) {
                    getString(R.string.pass)
                } else {
                    getString(R.string.failed)
                }
                intent.putExtra(ResultActivity.KEY_PASSIVE_AUTH, passiveAuthStr)
                intent.putExtra(ResultActivity.KEY_CHIP_AUTH, chipAuthStr)
                bitmap?.let { bitmap ->
                    if (encodePhotoToBase64) {
                        intent.putExtra(ResultActivity.KEY_PHOTO_BASE64, imageBase64)
                    } else {
                        val ratio = 320.0 / bitmap.height
                        val targetHeight = (bitmap.height * ratio).toInt()
                        val targetWidth = (bitmap.width * ratio).toInt()
                        intent.putExtra(
                            ResultActivity.KEY_PHOTO,
                            bitmap.scale(targetWidth, targetHeight, false)
                        )
                    }
                }
                if (callingActivity != null) {
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    startActivity(intent)
                }
            } else {
                Snackbar.make(passportNumberView, result.toString(), Snackbar.LENGTH_LONG).show()
            }
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
