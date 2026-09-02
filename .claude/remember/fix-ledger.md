# Fix ledger
> Non-blocking review findings. One bullet per item. Delete the bullet when
> fixed, or when its anchor no longer exists. Written by /branch-review;
> consumed by /refactor (ledger mode).
>
> A bullet's path may be a glob when the same finding exists in every kit —
> `git grep -F "<snippet>" -- <path>` accepts one.

- `apps/scanner/app/src/main/java/com/tananaev/passportreader/LifecycleFence.kt` · "every one of those reads is itself inside a" · class doc's thread-safety proof says every `passes()` read is inside a `runOnUiThread { ... }` block or `onPostExecute` — `72e0b2c` added two more reads (`onAuthenticationError`/`onAuthenticationFailed`, MainActivity.kt:1816/1848) that are neither: they're inside a `BiometricPrompt.AuthenticationCallback` dispatched via `ContextCompat.getMainExecutor(this)`, a third main-thread pattern the doc doesn't enumerate · CONFIRMED (re-read LifecycleFence.kt:51-64 and the two new call sites) — failure scenario: a future contributor auditing new call sites for "which threading forms are safe here" reads this doc as exhaustive (two forms) and doesn't recognize a third executor-dispatched callback as the same hazard class, reintroducing an unfenced landing the way the original 11-site sweep missed the biometric path · 2026-09-02 @ 9584bc8
