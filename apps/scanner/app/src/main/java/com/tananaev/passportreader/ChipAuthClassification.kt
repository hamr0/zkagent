package com.tananaev.passportreader

/**
 * D51/D53's three-state chip-authenticity decision, extracted to a pure
 * object the same way [FailureTransition]/[PaneVisibility] extracted their
 * respective decisions — finding #8
 * (`docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`): this logic previously
 * lived entirely inline in [MainActivity.ReadTask.doInBackground] with zero
 * unit coverage.
 *
 * THREE states ([M0Probe.ChipAuthStatus]): `VERIFIED`, `NOT_SUPPORTED`,
 * `FAILED` — NOT_SUPPORTED must never render as "false"/"not verified"
 * (D51/D53); a 2026-09 real-device fix split the DG14 read (absent -> the
 * document simply doesn't carry the file -> NOT_SUPPORTED) from the
 * doEACCA challenge-response (file present but the protocol errors ->
 * FAILED) into two independent decisions, which is what [fromDg14] pins.
 *
 * ONLY the DECISION moves here — the I/O ([android.nfc.tech.IsoDep]
 * connect, `service.getInputStream`, `DG14File` parse, `service.doEACCA`)
 * stays inline in [MainActivity.ReadTask.doInBackground], same discipline
 * as [FailureTransition] leaving the actual NFC read inline and only
 * extracting the classification of its outcome.
 */
object ChipAuthClassification {

    /**
     * The DG14/doEACCA decision, decomposed into its three independent
     * evidence bits:
     *  - [dg14Readable] — did `service.getInputStream(EF_DG14)` +
     *    `DG14File` parsing succeed at all (the outer try/catch in
     *    `doInBackground` — `false` means the document carries no DG14).
     *  - [caInfosPresent] — once DG14 parsed, is
     *    `securityInfos.filterIsInstance<ChipAuthenticationPublicKeyInfo>()`
     *    non-empty.
     *  - [challengeSucceeded] — did every `service.doEACCA(...)` call for
     *    those infos complete without throwing (only meaningful when
     *    [dg14Readable] && [caInfosPresent]; ignored otherwise, matching
     *    the inline code never reaching that branch).
     */
    fun fromDg14(dg14Readable: Boolean, caInfosPresent: Boolean, challengeSucceeded: Boolean): M0Probe.ChipAuthStatus =
        when {
            !dg14Readable -> M0Probe.ChipAuthStatus.NOT_SUPPORTED
            !caInfosPresent -> M0Probe.ChipAuthStatus.NOT_SUPPORTED
            challengeSucceeded -> M0Probe.ChipAuthStatus.VERIFIED
            else -> M0Probe.ChipAuthStatus.FAILED
        }

    /**
     * The combine rule for the two independent chip-authenticity
     * mechanisms — chip authentication ([ca], from [fromDg14]) and active
     * authentication ([aa], from [M0Probe.tryActiveAuth]'s `.first`):
     * either mechanism VERIFIED wins; else either FAILED beats
     * NOT_SUPPORTED (a genuine protocol failure is a stronger signal than
     * "this mechanism just isn't present"); the combined status is
     * NOT_SUPPORTED only if BOTH are. Byte-for-byte equivalent to the
     * inline `when` this replaces in `doInBackground`.
     */
    fun combine(ca: M0Probe.ChipAuthStatus, aa: M0Probe.ChipAuthStatus): M0Probe.ChipAuthStatus = when {
        ca == M0Probe.ChipAuthStatus.VERIFIED || aa == M0Probe.ChipAuthStatus.VERIFIED -> M0Probe.ChipAuthStatus.VERIFIED
        ca == M0Probe.ChipAuthStatus.FAILED || aa == M0Probe.ChipAuthStatus.FAILED -> M0Probe.ChipAuthStatus.FAILED
        else -> M0Probe.ChipAuthStatus.NOT_SUPPORTED
    }

    /** §6.2 item 16's plain-language value for the log entry's `Chip auth`
     * line — owner-approved D53 text, byte-identical to what was inline in
     * [MainActivity.chipAuthLabel]. NOT_SUPPORTED must never read as
     * "false"; only FAILED contains "Not verified". */
    fun label(status: M0Probe.ChipAuthStatus): String = when (status) {
        M0Probe.ChipAuthStatus.VERIFIED -> "Verified — this document's chip proved it is genuine"
        M0Probe.ChipAuthStatus.NOT_SUPPORTED -> "Not supported — this document has no chip authenticity check"
        M0Probe.ChipAuthStatus.FAILED -> "Not verified — the chip check did not pass"
    }

    /** The `chip_auth (D21 payload field)` technical value in the
     * baseReport — byte-identical to what was inline in
     * [MainActivity.continueAfterRead]. Single source with [label]: both
     * derive from the SAME [M0Probe.ChipAuthStatus], never two
     * independently-tracked variables that could drift. */
    fun technical(status: M0Probe.ChipAuthStatus): String = when (status) {
        M0Probe.ChipAuthStatus.VERIFIED -> "passed"
        M0Probe.ChipAuthStatus.FAILED -> "failed"
        M0Probe.ChipAuthStatus.NOT_SUPPORTED -> "absent"
    }
}
