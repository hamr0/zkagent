package com.tananaev.passportreader

/**
 * D58 step 3 (findings #2/#3, the ownership-refactor's headline target) —
 * the lock-time snapshot of a verified handoff request. Constructed EXACTLY
 * ONCE, by `MainActivity.lockModeAndArm`, at the moment a mode-B (or
 * mode-A-via-handoff) session commits to a specific verified request (see
 * that function's doc). Everything the read/derive/mint pipeline needs
 * from that request — the verified payload itself ([request]), the origin
 * it was verified for ([origin]), and the site title derived from that
 * origin ([site]) — travels from there on as THIS ONE immutable value,
 * passed as a function PARAMETER through `startSession` -> `ReadTask` ->
 * `onPostExecute` -> `continueAfterRead` -> its background `Thread` ->
 * `promptAndMint` -> `mintAndMaybeHandoff`. After lock time, none of those
 * functions reads `MainActivity.pendingHandoff`/`verifiedRequest` again —
 * see those two fields' own doc, and `docs/logs/M2-OWNERSHIP-AUDIT-
 * 2026-09-02.md` / `docs/logs/M2-RACE-ANALYSIS-2026-09-02.md` for why that
 * was findings #2/#3 (three writers each, cross-thread, non-`@Volatile`
 * reads, no staleness guard) and how it was exploitable (findings #10/#11).
 *
 * A plain, Android-free `data class` (`val`s only) — same reasoning as
 * [PaneVisibility] / [MintGate] / [HandoffAdmission] / [MintPromptText]:
 * the decision/data this class holds needs to be testable without an
 * Activity, under this module's `unitTests.isReturnDefaultValues = true`.
 *
 * WHY THIS MAKES A STALENESS GUARD UNNECESSARY, BY CONSTRUCTION (not by a
 * check — see `MainActivity.continueAfterRead`'s doc for where the old
 * guards lived and were removed): before this step, `continueAfterRead`'s
 * background `Thread` and `mintAndMaybeHandoff` each independently
 * re-read the mutable `pendingHandoff`/`verifiedRequest` fields, so a
 * defensive null re-check existed at each site in case a LATER
 * `beginHandoffVerification` (a second handoff, or an admitted `av://`
 * intent — see [HandoffAdmission]'s doc for why that guard is KEPT rather
 * than removed) had overwritten them in between. Now, every read
 * downstream of lock time is a read of THIS class's `val` fields, captured
 * once and passed by parameter: a later `beginHandoffVerification` call
 * can still overwrite `MainActivity.pendingHandoff`/`verifiedRequest`, but
 * it cannot reach back in time and change what an already-constructed
 * [AuthorizedHandoff] instance carries — there is no setter, and a `val`
 * cannot be reassigned. The one remaining non-null assertion
 * (`continueAfterRead`'s `snapshot!!`, taken once, before the pipeline's
 * background `Thread` opens) documents that invariant rather than
 * re-deriving it a second time downstream, matching this file's own
 * existing `origin!!` convention.
 *
 * Deliberately does NOT hold the `HandoffClient.PendingHandoff` this
 * request was fetched from: nothing downstream of lock time (the
 * read/mint pipeline) reads `clientId`/`requestUri` off it — every value
 * the pipeline signs, binds, or reports (`nonce`, `response_uri`, `state`,
 * the tier) comes from [request]'s own verified JSON, and `origin`/[site]
 * are derived from [request] exactly once, here, at construction.
 * `scopeDomain` (the parsed host of [origin]) is likewise NOT a field —
 * that parse can fail (an origin with no parseable host, an existing,
 * unchanged refusal path), so it stays a derivation performed where it
 * always was, just sourced from THIS snapshot's [origin] instead of the
 * mutable field.
 *
 * @param request the verified request object ([RequestTrust.VerifiedRequest])
 *   this session is locked to.
 * @param origin [request]'s own [RequestTrust.VerifiedRequest.origin],
 *   named again here only because it is what every downstream call site
 *   already threads as a parameter under this exact name — not a second,
 *   independently-derived value.
 * @param site `MainActivity.siteTitleFor` of [origin] — the §6.2 item 16
 *   (D46) log-entry / biometric-prompt title (finding #11), computed once.
 */
data class AuthorizedHandoff(
    val request: RequestTrust.VerifiedRequest,
    val origin: String,
    val site: String,
)
