package com.tananaev.passportreader

/**
 * FIX (S67-POC device evidence, 2026-09-06) — root cause: a cold `av://`
 * launch of [RegularActivity] (`singleTask`, fixed `screenOrientation`)
 * into a brand-new task, started from a non-Activity-context caller (a
 * browser/camera tap or `adb shell am start` — both routes the PRD
 * documents, see AndroidManifest.xml's activity comment), can trigger the
 * PLATFORM's own follow-up relaunch (`ActivityTaskManager`: "activity-
 * requested-portrait", a second `START ... with LAUNCH_SINGLE_TASK` issued
 * by `com.android.systemui`, ~150-300ms after the first — device-confirmed,
 * non-deterministic, ~1-in-5 to ~1-in-10 cold launches in on-device
 * reproduction). This briefly creates a SECOND live `MainActivity`
 * instance while the first is still mid-verification of the SAME `av://`
 * handoff — exactly the scenario [LifecycleFence]'s own class doc
 * anticipates ("Activity recreation... briefly has two MainActivity
 * instances alive with two independent lifecycles").
 *
 * [LifecycleFence] already does its ONE job correctly here: a verification
 * outcome landing after its OWNING instance's `onDestroy` never touches
 * that instance's UI. But before this fix, "never touch this instance's
 * UI" meant the outcome — including a REFUSAL the user must be told about
 * (§6.5 S1/D74, §6.7 operator-policy) — was simply discarded. Nothing else
 * ever showed it: the destroyed instance's own attempt to open a NEW
 * `AlertDialog` window on an already-torn-down Activity is rejected by
 * `WindowManager` (device evidence: a `BadTokenException`-shaped stack
 * trace at `MainActivity.showBlockingNotice`), and the surviving sibling
 * instance has no way to learn what the dying one decided.
 *
 * This object is the one relay a dropped REFUSAL message passes through so
 * the very next live instance (an already-resumed sibling, or the next
 * `onResume` of a freshly created one) shows it exactly once — restoring
 * the invariant this FIX pass exists to establish: "a refusal is rendered
 * by the instance that survives." Deliberately NOT a wider promotion of
 * `pendingHandoff`/`verifiedRequest` to companion/process scope (a bigger,
 * riskier change than this pass makes — see `.claude/remember/findings.md`
 * if that is ever revisited): this relay carries only the already-decided,
 * value-free, user-facing message text — nothing that could let a second
 * instance resume or arm a scan from it.
 *
 * `@Volatile` (not `synchronized`): [stash] runs on whichever main thread
 * lands the fenced verification callback (the dying instance's own main
 * thread — Android has exactly one), and [consume] runs on a LATER
 * instance's main thread — same physical thread, never truly concurrent,
 * but `@Volatile` keeps this correct even if that ever changes, at zero
 * cost.
 */
object DroppedOutcomeRelay {

    @Volatile
    private var pendingMessage: String? = null

    /** Called from a fenced UI landing that would otherwise silently drop
     * a user-facing refusal message because its owning instance is already
     * destroyed. */
    fun stash(message: String) {
        pendingMessage = message
    }

    /** Called by a live instance (this pass: `MainActivity.onResume`)
     * before it would otherwise sit idle with nothing to relay — consumes
     * (clears) the pending message so it is shown exactly once, never
     * re-shown on a later resume. Returns null when there is nothing
     * pending, which is the overwhelmingly common case (no dual-instance
     * race occurred). */
    fun consume(): String? {
        val message = pendingMessage
        pendingMessage = null
        return message
    }
}
