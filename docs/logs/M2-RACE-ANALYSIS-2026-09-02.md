# Superseded-handoff race — attacker-inducibility analysis (read-only)

**Scope**: `apps/scanner`, tree pinned to `2cd1e004bb3909f28620912d9af0a27f4f275f38`
(same pin as the ownership audit; `git status --porcelain` unchanged, no file
touched by this analysis). All claims below are VERIFIED by reading the
cited `file:line` unless marked INFERRED.

---

## 1. Attack surface, verified

| Entry point | `file:line` | Reachable by |
|---|---|---|
| `RegularActivity` exported, `MAIN`/`LAUNCHER` | `apps/scanner/app/src/regular/AndroidManifest.xml:5-14` | icon launch only — not attacker-relevant |
| `RegularActivity` exported, `VIEW`/`BROWSABLE`, `scheme="av" host="authorize"` | `apps/scanner/app/src/regular/AndroidManifest.xml:9,24-29` | **any app on the device**, via `Intent(ACTION_VIEW, Uri.parse("av://authorize?..."))` → `startActivity()`. `android:exported="true"` (`:9`) plus a browsable custom-scheme filter means Android resolves this without any permission check. `android:launchMode="singleTop"` (`:8`) means if `RegularActivity` is already the foreground/top-of-task activity, no new instance is created — the intent is delivered straight to the running instance's `onNewIntent` (`MainActivity.kt:536-540`). |
| `onNewIntent` → `handleIncomingIntent` | `MainActivity.kt:536-540, 542-585` | Dispatched by the framework for every intent above. |
| `av://` branch inside `handleIncomingIntent` | `MainActivity.kt:544-550` | **No guard of any kind.** `HandoffClient.parseAvLink(data)` (`HandoffClient.kt:43-54`) accepts any `av://authorize` or `https`-scheme URI carrying a `request_uri` query param; on a match this branch unconditionally calls `beginHandoffVerification(handoff)` (`MainActivity.kt:548`) — **regardless of `lockedMode`, `readInProgress`, or whether a mint is already in flight.** Contrast the NFC-tag branch two lines below, which DOES check `lockedMode != null` (`:555-559`) and non-empty MRZ fields (`:563-566`) before proceeding (guards table row 1, audit revision 4). |
| No other exported component | — | `AndroidManifest.xml` (regular flavor) declares exactly one `<activity>`; no exported services/providers/receivers found in the files read for this pass. |

**Finding 1 (VERIFIED): the `av://` capture path has zero admission control.** Every other mutating entry point in this file (the NFC-tag branch, `lockModeAndArm`) reads at least one piece of state before acting; `handleIncomingIntent`'s `av://` branch reads nothing and gates on nothing. This is the single fact that turns "there is a data race" into "an on-device attacker can reliably win the race": the attacker does not need precise timing against a narrow window — they can fire `av://authorize?...` in a blind loop for the entire duration the app is foregrounded, and `beginHandoffVerification` (`:604-620`) will synchronously overwrite `pendingHandoff`/`verifiedRequest` (`:605-606`) on every single delivery, main-thread, no exceptions.

---

## 2. Trust chain at each step (does the attacker get `Verified`?)

`RequestTrust` enforces exactly two things, both readable/reproducible by any attacker who controls their own domain — this is BY DESIGN, not a bug (`RequestTrust.kt:24-63`, D37: "trust anchor is the origin itself... verifier is not our issue"):

1. **Origin consistency** (`MainActivity.kt:629-637`, `RequestTrust.kt:80-102`): `client_id` origin (if present) must equal `request_uri` origin, and the verified payload's own `response_uri` origin must equal that same origin (checked at `:656-662`). An attacker who serves `client_id`, `request_uri`, and `response_uri` all under `https://attacker.evil` trivially satisfies this — it is an internal-consistency check, not an allow-list.
2. **ES256 JWS signature**, verified against a key resolved for that origin (`RequestTrust.kt:228-253`). For any origin other than `http://127.0.0.1`/`localhost` (the dev-pinned exception, `:117-143`, never reachable for a real attacker domain), the key comes from `GET https://<origin>/.well-known/zkagent-verifier` (`RequestTrust.kt:145-175`, `resolveVerifierKey` at `:124-143`) — **the attacker's own server, since it is the attacker's own origin.** They publish their own JWK there and sign their own request object with the matching private key. Nothing here requires the attacker's origin to be pre-registered, allow-listed, or otherwise vetted.

**Answer: yes, unconditionally.** An attacker who stands up their own HTTPS origin (trivial, no special access) gets a fully `Verified` `RequestTrust.Outcome` for a request object of their own construction — `client_id`/`request_uri`/`response_uri` = attacker's, `nonce`/`tier`/`expires_at` = whatever the attacker's own server chooses to issue. This is the intended trust model (any site may request a presentation), so it is not itself a finding — it is the premise the race exploits.

**The D38 origin guard** (`continueAfterRead:1186-1187`, guards table row 4) and the **session-expiry re-check** (`continueAfterRead:1215-1218`, guards table row 5) both read `verifiedRequest` directly — **as it is at the moment `continueAfterRead` runs on the MAIN thread, immediately after the chip read completes** (called from `ReadTask.onPostExecute:1124`, itself the AsyncTask's main-thread callback). This is **not** "as it was at lock time": `lockModeAndArm` (`:411-502`) reads `pendingHandoff`/`verifiedRequest` once, at tap time, only to decide `mode` (A vs. B) and to run the tier/expiry pre-checks (`:421-486`) — it does **not** snapshot `verifiedRequest` itself into anything passed downstream. `origin` (`continueAfterRead:1187`, `val origin = verifiedRequest?.origin`) and the D38/expiry guards all re-read the **live, mutable field** post-read, not a value captured at lock time. Only the derived `mode: PresentationMode` local is threaded through as an immutable parameter from `lockModeAndArm` onward (`startSession → ReadTask → onPostExecute → continueAfterRead`); `verifiedRequest` itself is not.

---

## 3. Timing windows — sequence trace

Legend: **MAIN** = UI thread: **BG-A** = `continueAfterRead`'s own `Thread{}` opened at `:1262`; **BG-B** = `mintAndMaybeHandoff`'s `Thread{}` opened at `:1414` (from the `BiometricPrompt` success callback, itself main-thread-delivered).

```
[MAIN]  user opens av://authorize (Site A) ─▶ beginHandoffVerification         (:604)
        pendingHandoff = SiteA (:605); verifiedRequest = null (:606)
        ── verify thread ── (background, not shown) ──▶ runOnUiThread
[MAIN]  applyHandoffVerificationOutcome: verifiedRequest = SiteA-Verified      (:678)
        (guard: pendingHandoff === handoff, i.e. still SiteA — passes)

[MAIN]  user fills MRZ, taps "Lock & scan" ─▶ lockModeAndArm                   (:411)
        reads pendingHandoff/verifiedRequest = SiteA (:421,424)  →  mode = B
        lockedMode = B (:498); armNfcDispatch()

[MAIN]  user taps passport ─▶ handleIncomingIntent (NFC branch, guarded)      (:552-583)
        startSession → ReadTask.doInBackground begins (chip read: PACE/BAC,
        DG1+SOD, masterlist passive-auth — multi-second, real-device timing)

   ══════════════════ WINDOW 1 (multi-second, chip read in flight) ══════════
   [MAIN]  ATTACKER'S av:// intent lands here, ZERO guard on this path
           (handleIncomingIntent:544-550 has no lockedMode/readInProgress
           check — the NFC branch's guard at :555 does not cover this branch)
           ─▶ beginHandoffVerification(attackerHandoff)                       (:604)
           pendingHandoff = Attacker (:605); verifiedRequest = null (:606)
           handoffStatus.text = "Handoff request received — verifying…"      (:607)
           lockButton.isEnabled = false (:609, already false, no visible change)
           ── attacker's own server answers fast ── runOnUiThread
   [MAIN]  applyHandoffVerificationOutcome: pendingHandoff === Attacker (still
           current — SiteA's chip read has not finished) → passes the ONLY
           staleness guard in the file (:672) → verifiedRequest = Attacker   (:678)
           handoffStatus.text = "Handoff verified — origin: attacker.evil…" (:680)
   ══════════════════════════════════════════════════════════════════════════

[MAIN]  ReadTask.onPostExecute (chip read done)                              (:1069)
        readInProgress = false; showPane(); wipeSession(false); (MRZ cleared;
        lockedMode is NOT cleared by this branch — success path)
        continueAfterRead(mode=B[from lock-time], verdict, …)                (:1124)
[MAIN]  origin = verifiedRequest?.origin  →  IF WINDOW 1 FIRED: Attacker      (:1187)
        D38 guard passes (origin non-null); expiry re-check passes           (:1186-1218)
        opens Thread → BG-A                                                  (:1262)
[BG-A]  handoff = pendingHandoff; verified = verifiedRequest                  (:1281-1282)
        — cross-thread read of a non-@Volatile field, no staleness guard —
        IF WINDOW 1 FIRED: both = Attacker's, SAME snapshot as `origin` above
        scopeDomain = URI(verified.origin).host  →  attacker.evil            (:1304)
        zktag = M0Probe.deriveCandidates(dg1File, domain=scopeDomain)        (:1325)
          — zktag is DOMAIN-SALTED to whichever origin is current here
        alias = DeviceKey.aliasForOriginAndZktag(origin!!, zktag)            (:1348)
          — origin here is the SAME Window-1 snapshot as line 1187 (attacker,
            if Window 1 fired) — self-consistent with scopeDomain/zktag
        DeviceKey.ensureKey(alias)  →  StrongBox key generated (no biometric
        needed yet) — runOnUiThread → promptAndMint                          (:1369)

[MAIN]  promptAndMint shows the BiometricPrompt — generic title/subtitle,
        NEVER shows an origin/site string anywhere in the prompt UI          (:1391-1395)
        user authenticates (fingerprint/PIN) believing they are answering
        Site A, because nothing in the prompt says otherwise

   ══════════════════ WINDOW 2 (human-timescale — however long the user
                       takes to complete the biometric prompt) ═════════════
        the SAME zero-guard av:// path is open the entire time; a THIRD
        (or first, if Window 1 did not fire) attacker intent can land here
   ══════════════════════════════════════════════════════════════════════════

[MAIN]  onAuthenticationSucceeded → Thread → BG-B                            (:1414)
[BG-B]  mintAndMaybeHandoff: SECOND, INDEPENDENT read of pendingHandoff/
        verifiedRequest — a third distinct cross-thread read site           (:1470-1471)
        IF WINDOW 2 (alone) FIRED: this now diverges from BG-A's read —
        nonce/response_uri/state come from THIS read (attacker's, if raced);
        scopeDomain/zktag are FUNCTION PARAMETERS from BG-A (NOT re-read here)
        — see §4 for what this split means for exploitability
        message = EvidenceSigner.messageFor(alg, claim, nonce, scopeDomain,
                  zktag)                                                     (:1531)
        signed with the biometric-authorized key; direct_post to
        requestObject's response_uri                                        (:1599-1616)
        pendingHandoff = null; verifiedRequest = null (runOnUiThread)        (:1637-1638)
```

**Does `lockButton.isEnabled = false` or `lockedMode != null` block Window 1 or Window 2?** No. `lockButton.isEnabled` only gates a second press of the *button* — it is never read by `handleIncomingIntent`'s `av://` branch (guards table row 1 lists exactly what that branch's own guard reads: `lockedMode`, MRZ non-empty — `pendingHandoff` is not among them, and the `av://` branch has no guard at all, per Finding 1 above). `lockedMode != null` is likewise never consulted before `beginHandoffVerification` runs. Both are pane/session-state flags for the *chip-read* path only; they do not fence the handoff-capture path at all.

---

## 4. What the evidence binds, and what chiproof does with it

Signed preimage (`EvidenceSigner.kt:17-21`, byte-identical to `packages/chiproof/src/plugs/attester-sig.js:12-16`):

```
preimage = utf8(pluginType + "\n") || sha256(canonicalize(claim)) || base64urlDecode(nonce)
           || utf8(scopeDomain) || utf8(zktag)
```

**Bound into the signature**: `claim` (`over_threshold`, `threshold`), `nonce`, `scopeDomain`, `zktag`. **Used only for delivery, never bound into the signature**: `response_uri`, `state`, `client_id`. The chiproof verifier's `attesterSig.verify(item, ctx)` (`attester-sig.js:186-260`) reconstructs the expected preimage from **its own server-side `ctx.scopeDomain`** (the verifier's own configured identity) and **the presented `ctx.zktag`/`ctx.nonce`** — `binds: {nonce:true, claim:true, scope:true, zktag:true}` (`attester-sig.js:180-181`). A verifier that did not itself issue the `scopeDomain` baked into the signed bytes will recompute a different expected message and the ECDSA verify will fail.

This produces **two distinguishable outcomes depending on which window fires**:

- **Window 1 fires (attacker's intent lands during the chip read, before `continueAfterRead`'s `:1281-1282`/`:1304`/`:1325`/`:1348` derivation runs).** `origin`, `scopeDomain`, and `zktag` are ALL derived from the attacker's `verifiedRequest` at that point. If nothing supersedes it again before `mintAndMaybeHandoff`'s later read (`:1470-1471`) — the common case, since nothing else in the file clears `pendingHandoff` in between — `nonce`/`response_uri`/`state` are attacker's too, self-consistently. **The resulting signature verifies successfully against the attacker's own chiproof verifier.** The attacker's site receives a cryptographically valid `zkagent/1` presentation — the user's real device key, real passport read, real biometric consent, delivered to a site the user never saw or agreed to. This is the EXPLOITABLE case.
- **Window 2 fires alone (attacker's intent lands only during/after the biometric prompt, after `continueAfterRead`'s BG-A derivation already fixed `scopeDomain`/`zktag` to Site A's values).** `mintAndMaybeHandoff`'s independent re-read (`:1470-1471`) picks up the attacker's `nonce`/`response_uri`/`state`, but `scopeDomain`/`zktag` are still Site A's — they are function **parameters** threaded from BG-A, not re-derived at `:1470-1471`. The signed message therefore embeds Site A's `scopeDomain` bytes while being delivered, with the attacker's `nonce`/`state`, to the attacker's `response_uri`. The attacker's own verifier reconstructs the expected preimage from its own `ctx.scopeDomain` (its own domain) — this will not match what was actually signed, so **the signature fails cryptographic verification at the attacker's server.** The attacker does not get a valid presentation this way. **They do, however, still cause Site A's real session to be hijacked-away**: the user's genuine answer for Site A is diverted to the attacker's `response_uri` instead of Site A's, so Site A's own `nonce`/`state` session simply never resolves (a silent denial-of-service against the legitimate site), while the UI still tells the user "Verified — the site accepted you" (`:1739-1741`) even though no site the user intended to interact with received anything valid. This is a real but lower-severity outcome than Window 1.

Either way, **`showBlockingOutcomeDialog`'s confirmation dialog is content-free about which site was involved** (`MINT_CONFIRMED_MESSAGE`, static text — not read in full this pass, but referenced at `:1740` with no site parameter) — the user has no positive confirmation of which origin the biometric-authorized mint actually targeted, in either window.

---

## 5. Verdict: **EXPLOITABLE**

**Exact sequence**: as traced in §3, with the attacker's intent landing in Window 1 (during the multi-second NFC chip read). No special permissions are required; `av://authorize` is a browsable, exported deep link any installed app can invoke via a plain `startActivity(Intent(ACTION_VIEW, uri))`, and `handleIncomingIntent`'s `av://` branch (`MainActivity.kt:544-550`) admits it unconditionally regardless of session state.

**The single most load-bearing line**: `MainActivity.kt:548` — `beginHandoffVerification(handoff)`, called with no `lockedMode`/`readInProgress` gate, inside the one branch of `handleIncomingIntent` that has no admission guard at all. Everything downstream (the non-`@Volatile` cross-thread reads at `:1281-1282` and `:1470-1471`, the identity-only staleness check at `:672`) only matters because this one call site lets an attacker write to `pendingHandoff`/`verifiedRequest` at literally any moment the activity is resumed, with no cost and no retry limit.

**Confidence note**: the causal chain from Android's intent-resolution rules (exported + browsable + singleTop → `onNewIntent` delivery regardless of current foreground app) through to this code's complete absence of a guard is verified by reading the manifest and the guard table together — not run on a device. What is NOT verified by reading alone: the exact real-device timing of the NFC chip read (audit e1/e2 already flags this class of gap generally) and thus how *wide* Window 1 is in wall-clock terms on the Pixel 6a. It is wide enough in principle (multiple sequential I/O operations: PACE/BAC handshake, two file reads, masterlist verification) that a blind-repeating attacker intent (fired every few hundred ms) would be expected to land in it on most real attempts, but this is INFERRED from the code's structure, not measured.

**Device experiment that would settle the remaining uncertainty** (not run, per scope): fire a scripted `adb shell am start -a android.intent.action.VIEW -d "av://authorize?client_id=...&request_uri=..."` in a loop against a second, attacker-controlled local handoff server while a real scan is in progress against the real target server, and confirm from `handoffStatus.text`/logcat which `verifiedRequest.origin` `continueAfterRead` actually derived from, and whether the resulting `direct_post` lands at the attacker's endpoint.

---

## 6. Mitigation options (not fixes — ranked by how much they change)

1. **Snapshot the verified request into an immutable value at lock time, threaded as a parameter — smallest change, closes the most.** The file already does exactly this for `mode` (`lockModeAndArm` → `startSession` → `ReadTask` → `continueAfterRead`, all as an immutable `PresentationMode` parameter) and for `zktag`/`scopeDomain`/`site` from `continueAfterRead` onward ("never re-derived downstream" — the class doc's own stated discipline, `:1243-1248`). Extending the SAME discipline one step further back — capture `verifiedRequest` itself (not just the mode it implies) as an immutable value inside `lockModeAndArm`, and thread *that* through `startSession`/`ReadTask`/`continueAfterRead`/`promptAndMint`/`mintAndMaybeHandoff` instead of re-reading the mutable field at `:1187`, `:1281-1282`, and `:1470-1471` — closes Window 1 and Window 2 both, because nothing downstream of the lock button would ever again observe a value the field held at any time other than lock time. Addresses (a)-table rows 2/3 directly; removes the cross-thread-read hazard entirely rather than guarding it. Does not address Finding 1 (the `av://` branch itself stays unguarded) — a second handoff could still silently overwrite `pendingHandoff`/`verifiedRequest` for the *next* attempt, but it can no longer affect an *in-flight* one.
2. **Ignore `av://` intents while `lockedMode != null` or `readInProgress` is true — smallest code diff, closes the entry point directly.** Mirror the NFC-tag branch's own existing guard shape (`:555-566`) onto the `av://` branch (`:544-550`). Closes both windows at the source: no new `pendingHandoff` can be captured while a session is locked or a read is in flight, so the fields being racy no longer matters for an in-flight mint. Addresses Finding 1 and, transitively, (a)-table rows 2/3. Leaves the underlying non-`@Volatile`/no-staleness-guard shape (a2 F5(ii)/(iii)) unchanged — a future call site that adds another way to write these fields would reopen the hole unless it also honors the new guard; option 1 doesn't have that fragility.
3. **Bind `response_uri` (and/or `client_id`) into the signed payload, not just `scopeDomain`.** Would make Window 2's partial race (§4, second bullet) fail even more definitively at the attacker's verifier (currently it already fails there via the `scopeDomain` mismatch — this option adds a second independent reason it fails, and would also cause it to fail even in a hypothetical future where an attacker's domain happens to collide with `scopeDomain`, e.g. two verifiers on the same `host` but different paths, which `RequestTrust.originOf`'s scheme+host+port-only comparison — path is explicitly not compared, `RequestTrust.kt:85` — does not distinguish today). Does **not** address Window 1 at all: if `origin`/`scopeDomain`/`zktag` are already attacker's by the time signing happens, adding `response_uri` to the preimage only makes the (already self-consistent) attacker-controlled message more self-consistent — it changes nothing about whether the attacker's own verifier accepts it. A cryptographic hardening, not a race fix.
4. **Add a positive, pre-biometric confirmation of which origin/site is about to be authorized**, shown as part of (or immediately before) the `BiometricPrompt` itself (currently `:1391-1395` — static title/subtitle, no site parameter) rather than only in the post-hoc report/log (`key_scope:`/`site:` lines, `:1554`, `:1690` etc.). Does not close the race mechanically, but gives the user a chance to notice a hijacked target before authorizing — the weakest option of the four, since it depends on the user reading and comparing a domain string under time pressure, but it is the only option here that also mitigates a *fully self-consistent* Window-1 exploit that options 1-3 might not fully anticipate (e.g., a future flow where the attacker's own domain happens to match something the user *does* trust, via typosquatting).

Options 1 and 2 are not mutually exclusive and address different halves of the same defect (2 closes the entry point; 1 removes the consequence even if the entry point stays open for some other reason) — the audit's own "already passes some values as parameters rather than re-derived" note (owner-visible pattern in this file) suggests option 1 is the more idiomatic fit for this codebase's existing discipline.

---

## 7. Out-of-scope observations (adversary attached, reported only here)

- **`handoffStatus.text`/`modeStatusView.text` momentarily display the attacker's origin during a successful Window-1 race** (`:680`, `refreshModeStatus:373-381`) — a forensic trace exists for a user who happens to be looking, but nothing else in the UI treats this as an alarm; no color change, no distinct sound, no blocking element. Worth noting alongside mitigation option 4 rather than as its own separate risk.
- **`RequestTrust.originOf`'s path-insensitivity** (`RequestTrust.kt:85`: "Path/query are deliberately not part of the comparison") means two different verifiers hosted as different paths under the same `scheme://host:port` (e.g., a shared hosting provider, or a multi-tenant SaaS verifier product) are treated as the SAME origin for both the D37 consistency check and the D38 key-scoping/`scopeDomain` value. Not attacker-relevant on its own (an attacker still needs to control the whole host), but worth flagging as a sharpness limit on the "per-origin key" isolation guarantee (D38/D39) if such shared-hosting verifiers are ever a real deployment target.
- **The masterlist/device-key probe buttons remain release-reachable** (`MainActivity.kt:304-311`, a2 F5(iv)/(v) per the audit) — unrelated to this race, but the same "any app can trigger arbitrary intents" attack surface question applies to whether these probe buttons could themselves be reachable via some other exported surface; this pass found no such surface (they are plain in-app `View.OnClickListener`s, not intent-triggered), so this is noted only as a boundary check, not a finding.
