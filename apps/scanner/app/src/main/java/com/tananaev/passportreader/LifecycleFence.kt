package com.tananaev.passportreader

/**
 * FIX pass (D57 exit criterion 2 / findings.md #5, "zero async-cancellation
 * discipline") — the owner-approved mechanism for fencing `MainActivity`'s
 * unfenced background-thread main-thread landings. No `onDestroy` override
 * and no lifecycle guard of any kind existed anywhere in this module before
 * this pass (verified by grep); background work therefore ran to
 * completion regardless of Activity lifecycle, and landed on the main
 * thread via `runOnUiThread` against a possibly-destroyed Activity
 * instance — `runOnUiThread` changes THREAD, not LIVENESS, so it is not by
 * itself a fence.
 *
 * **A pure fence class, deliberately, with no new Gradle dependency** —
 * the owner explicitly chose this over a coroutines/`lifecycleScope`
 * rewrite. Testable under this module's `unitTests.isReturnDefaultValues =
 * true` config the same way [PaneState]/[PaneVisibility]/[SessionDisplay]
 * are: it touches no Android framework class at all, so nothing here is a
 * non-functional stub under that config.
 *
 * **Per-`MainActivity`-INSTANCE field, NOT a singleton and NOT a companion
 * object — this is the single most important correctness property of this
 * class.** [PaneVisibility] is a stateless `object` precisely because it
 * carries no mutable state; a fence is the opposite case; it exists ONLY
 * to carry mutable state, and Activity recreation (rotation, process
 * restart) briefly has two `MainActivity` instances alive with two
 * independent lifecycles. A singleton/companion-object fence retired by
 * the dying instance's `onDestroy` would stay retired forever — including
 * for the brand-new instance's own, still-live background work — which
 * would silently and permanently block every async landing in the
 * recreated Activity. Each `MainActivity` instance therefore constructs
 * its OWN `LifecycleFence` as a plain instance field (see
 * `MainActivity.fence`), exactly like `reportLog`/`paneState` before it —
 * there is no shared or static storage anywhere in this class for a
 * singleton bug to hide in.
 *
 * **Semantics — decided by the owner, not this class's to change:** a
 * fence drops a main-thread LANDING; it never cancels, interrupts, or
 * aborts in-flight background work. Retiring the fence only stops the
 * corresponding `runOnUiThread { ... }` body (or, for [MainActivity
 * .ReadTask.onPostExecute], the AsyncTask callback's own tail) from
 * touching Activity-owned UI/state after `onDestroy` — every background
 * `Thread`/`AsyncTask` this pass fences still runs to completion (or
 * failure) exactly as before. This is deliberate: e.g. aborting a
 * still-in-flight `direct_post` after the Activity is recreated (following
 * the biometric prompt) would kill a network call a verifier is already
 * waiting on. See call sites in `MainActivity` for where this trade is
 * applied, and the FIX report for the mint-after-destroy report-loss
 * consequence this trade knowingly accepts and does not fix.
 *
 * **Thread-safety, reasoned explicitly rather than assumed:** [retire] is
 * called from exactly one place, `MainActivity.onDestroy`, which the
 * Android framework always calls on the main thread. [passes] is read
 * from every fenced call site, and every one of those reads is itself
 * inside a `runOnUiThread { ... }` block (or, for the one `AsyncTask`
 * site, inside `onPostExecute`, which the framework also always dispatches
 * on the main thread) — so every write and every read of [alive] happens
 * on the main thread, never concurrently with each other. No
 * `@Volatile`/lock is added: there is no genuinely cross-thread read to
 * guard against. If a future call site reads [passes] from a background
 * thread instead, that call site is wrong and should be fixed to check
 * from the `runOnUiThread`/main-thread callback instead, not "fixed" by
 * adding synchronization here to paper over a race this class was
 * designed not to have.
 */
class LifecycleFence {

    private var alive = true

    /** `MainActivity.onDestroy`'s call — the ONLY writer. Idempotent:
     * retiring an already-retired fence is a no-op, never an error. */
    fun retire() {
        alive = false
    }

    /** `true` until [retire] is called, `false` forever after. Read at
     * every fenced call site, always from the main thread — see class doc. */
    fun passes(): Boolean = alive
}
