---
type: reference
title: zkagent — Customer Guide
status: stable
sources: [docs/product/zkagent-prd.md, docs/wiki/decisions.md, docs/wiki/milestones.md, docs/wiki/questions.md, docs/logs/M3-POC-EVIDENCE-2026-09-04.md, docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md, docs/logs/M2-SESSION-POC.md, docs/logs/M2-DEVICE-EVIDENCE.md, apps/demo/README.md, apps/scanner/README.md]
---

# zkagent — Customer Guide

A manual, not a pitch. Every scenario below is a table you can check against before you run it.
No code, no JSON, no shell commands here — the run recipe lives in `apps/demo/README.md`; this
guide only explains what happens and why.

## 1. What this is

zkagent proves one fact about you from the chip in your passport or ID card — for example "over
18" — without handing your name, date of birth, or any document data to the website asking. The
phone reads the chip, checks the government's own signature on it, and answers a yes/no question.
Nothing personal ever leaves the phone. The website runs its own verifier (its own small server,
its own database of who it has seen) — zkagent does not host anything, does not run a server on
anyone's behalf, and does not keep any list of users anywhere.

## Two answers, two places

The phone and the page never answer the same question, and treating one as a stand-in for the
other is the single most common misreading of a scan.

| | Answers | Possible values |
|---|---|---|
| The app's popup | Delivery status: did the phone read the chip and get its presentation to the verifier | "Read OK", "Sent", "Refused by the app" (a local check failed before anything was sent), "Verifier refused the upload" |
| The page | The site's verdict: what the verifier decided to do with what it received | "Allowed", "Not allowed", "Already registered at this site", "Key mismatch" |

Why they're separate: the app's own network call (`direct_post`) gets back a **receipt**, not a
verdict — a `200` with `{"accepted": true}` means only "the verifier received your presentation."
The verdict is a separate fact that reaches the page later, by the page polling the verifier — the
EU Age Verification Blueprint-shaped handoff zkagent follows (origin-bound request/response, per
`docs/wiki/decisions.md` D37; `av://` + `direct_post` as the primary handoff, milestones.md item 8).
Device-confirmed 2026-09-05 (`docs/logs/M3-POC-EVIDENCE-2026-09-04.md`): a reinstalled app showed
"Delivered — the website shows the result." — a true statement about delivery — at the exact
moment the page showed a refusal (`ok=true allowed=false reason=attester_key_mismatch`) — an
equally true statement about the verdict. Neither reading was wrong; they were answers to two different questions. **The app
cannot tell you the site's decision. Read the page.**

## 2. Glossary

| Term | Meaning |
|---|---|
| Tier A | Anonymous mode. One yes/no answer, no way to recognise you again. Default. |
| Tier B | Pseudonymous mode. Same yes/no answer, plus a per-site tag so that site (only) can tell if it's seen this document before. Opt-in, requires a tap-to-approve action on the phone. |
| Tier C | Attributed mode (name/expiry/other fields) — not built yet, planned for a future milestone (M3b). |
| zktag | The per-site tag tier B adds. Computed on the phone from the document and the site's address; the same document at the same site always makes the same zktag; the same document at a different site makes a different, unrelated zktag. |
| Verifier | The website's own server that checks what the phone sends and decides allow/refuse. Every operator runs their own; zkagent runs none. |
| Handoff link | The `av://` link the page hands to the phone (tap on the same device, or scan a QR code from a second device) to start a scan tied to that specific request. The scanner app only handles `av://` links today — it has no `https` App Link intent filter — so the demo must be run with the `av` link scheme for the same-device tap to work; the demo's `https` default is only a placeholder for a possible future app-link path, not something the scanner can open yet. |
| QR path | Scanning that same link with any ordinary camera app on a second phone — the scanner app has no built-in camera/QR feature of its own. |
| Attester key | A key the phone generates in its secure hardware (Keystore/StrongBox) and uses to sign a tier-B answer, so the site can trust it came from this phone. Requires a PIN/biometric approval each time it signs. |
| Chip authentication | A property some (not all) chips support that proves the physical chip is genuine, not a copy. Reported as true/false at tiers B/C only — tier A never emits it. |
| Masterlist | The public list of government signing certificates the phone checks the chip's signature against. Bundled in the app, verified before use. |
| Threshold | The age number being asked about (e.g. "over 18"). Fixed at 18 for the current demo; a small published list of allowed numbers is planned (§5). |
| Origin | The website address (scheme+host+port) the request came from. Keys, zktags, and thresholds are all scoped per origin. |
| Store | The website's own persistent record of zktags/keys it has seen. zkagent code stores nothing; the demo keeps a plain JSON file as an example. |

## 3. Tier comparison

| | Tier A (anonymous) | Tier B (pseudonymous) | Tier C (attributed) |
|---|---|---|---|
| What the site learns | One yes/no bit | Same bit, plus "have I seen this document at my site before" | Not built |
| What the phone sends | Bare answer, no signature, no key | Signed answer + zktag, from a device key | Not built |
| PIN/biometric prompt | None | Yes, before every mint | Not built |
| Scanner popup | Yes, every scan — confirmed, including two tier-A scans in a row | Yes, expected every scan — **unconfirmed observation: may not appear on a repeat tier-B scan** (an earlier report of a missing popup was traced to tier-B taps, not tier A; corrected 2026-09-04) | Not built |
| Page outcome text | "ALLOWED (over threshold)" or refusal reason | First visit: allowed/refused as normal. Repeat visit, same document: "Already registered at this site" | Not built |
| Where the verdict shows | Page, never the app (see "Two answers, two places" above) | Page, never the app (see "Two answers, two places" above) | Not built |
| Stored on the phone | Nothing persists beyond a value-free log entry | Same, plus a device key bound to (this site, this zktag) | Not built |
| Stored by the site | Nothing durable (a spent one-time nonce) | zktag + attester-key binding, kept until the operator clears it | Not built |
| Can the site recognise you again | No | Yes, at that one site only | Not built |
| Can two sites link you | No | No — zktags differ per site by design | Not built |
| Works with a cloned chip | N/A (no chip check in tier A) | Yes, if `chip_auth` is false — a clone mints the identical zktag as the real holder | Not built |
| Threshold | 18 (fixed) | 18 (fixed) | Not built |
| Status | POC passed, build in progress; trust-list (FR10, §6.3 item 8) moved to §6.5 S4, D78 — not an M3 criterion, needs a future attestation plug | POC passed, build in progress; trust-list (FR10, §6.3 item 8) moved to §6.5 S4, D78 — not an M3 criterion, needs a future attestation plug | M3b — not started, PRD-gated |

## 4. Tier rules — what each may and may never ask

| Tier | May ask | May never ask | Gating |
|---|---|---|---|
| A | One yes/no threshold bit | Any identifying field; anything that could stably identify you | None — always available, the default |
| B | Tier A's bit, plus "have I seen this document at my site before" (the zktag) | Your name, birthdate, or any document field; anything that links you across two different sites | Must be explicitly requested by the site — never inferred or defaulted |
| C | Booleans over identifying fields, from a published verb list (candidates named so far: name-match, expiry-bucket booleans; vocabulary not yet finalized) | Similarity scores, ever; any predicate off the published list | Refused outright unless the requesting site's issuer key is pinned in advance; the scanner refuses any tier-C request today; requires a future build (M3b) that is not started and is blocked on which verbs the vocabulary will contain |

## 5. Preset age thresholds and per-site locking (planned)

The published policy for which ages a site may ask about, and how it's locked once asked:

| Rule | Detail |
|---|---|
| Preset list | A fixed, published set of allowed ages: 15, 16, 18, 21, 60, 65. A site must request exactly one of these; anything else is refused loudly, no scan sent |
| Per-origin lock | The app locks onto the FIRST age it sees requested by a given site; a later request from that SAME site for a DIFFERENT age is refused — the site is told nothing about why |
| Named exceptions | A small, app-side allowlist of exact website addresses (no wildcards) permitted to ask more than one age — membership is the app's decision, never the site's own choice |
| Pre-tap question | Before you tap the scan/verify button, the app shows the exact question being asked (e.g. "This website asks if you are over 18"), read from the site's signed request, never a generic string |
| Today | The current demo requests age 18 only — there is no age picker yet |

**Status: planned, not yet built** (scanner-side items S1 and S2). Reinstalling the app resets
the per-site lock along with everything else in §10.

## 6. Scenarios

### 6.1 First-time tier A

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Tap "Prove you're over 18" | Handoff link + QR, waiting | — | Transaction created, mode A |
| 2 | Tap the link (or scan the QR from another phone) | Waiting for phone | Opens, no PIN prompt, ready to scan | — |
| 3 | Tap your document to the phone | — | Reads chip, mints bare answer, shows outcome popup with "Mode A, anonymous" | Verdict recorded: allowed, no evidence required |
| 4 | Look back at the page | "ALLOWED (over threshold)", the sent payload shown | — | — |

### 6.2 Second tier A (comparison table)

| Step | You do | Page shows | App shows |
|---|---|---|---|
| 1 | Repeat 6.1 with the same document at the same page | Header: "Scan 1 done — waiting for scan 2," then "Both scans received" | Same as 6.1, no PIN, new popup |
| 2 | — | Per-field diff table: only `nonce`, `issued_at`, `expires_at` differ; every other field reads "same" | — |

Device-confirmed 2026-09-04: exactly those 3 fields differ (there is no `signature` field in
this build — tier-A challenges are unsigned).

### 6.3 First-time tier B

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Tap "Prove you're a unique adult human" | Handoff link + QR, waiting | — | Transaction created, mode B |
| 2 | Tap the link / scan the QR | Waiting for phone | Button reads "Tap and verify" | — |
| 3 | Tap your document to the phone | — | PIN/biometric prompt naming the site, then outcome popup "Mode B, recognisable to this site" | New attester key bound — first time seen at this site |
| 4 | Look back at the page | "ALLOWED", not seen before, not already registered | — | zktag + key recorded |

### 6.4 Repeat tier B, same document, same site

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Tap "Prove you're a unique adult human" again | New transaction created | — | — |
| 2 | Tap the same document | — | PIN/biometric prompt again — expected every mint; **unconfirmed observation: the outcome popup may not appear on this specific repeat case** (see §3) | Attester key matched — recognised as returning |
| 3 | Look back at the page | "Already registered at this site", zktag seen before, already registered | — | zktag unchanged, no new record created |

### 6.5 Repeat tier B after verifier restart

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Operator kills and restarts the local verifier process | Page reloads once the server is back | — | Store reloaded from disk |
| 2 | Tap the same document again | "Already registered at this site" | Same flow as 6.4 | Attester key matched — binding survived the restart |

Device-confirmed 2026-09-04: both documents refused as already-registered after a server
kill-and-restart; zktags unchanged across the restart.

### 6.6 Different document, tier B, same site

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Tap "Prove you're a unique adult human" with a second, different document at the same site | "ALLOWED", not seen before | New PIN/biometric prompt, new key bound | New, distinct zktag registered — the site cannot tell this is the same phone as the first document (keys/zktags are isolated per document, not just per site) |

### 6.7 Wrong MRZ details typed in

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Type an incorrect document number or date in the app's entry form, then scan | Unaffected — no transaction reaches it | Blocking popup: check the details and try again; the fields you typed are kept so you can fix them | Nothing — no data reached the verifier |

### 6.8 Card lifted mid-read

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Move the document away from the phone before the read finishes | Unaffected, still waiting | Blocking popup: "couldn't read the card, keep the card at the top of your phone"; nothing needs re-typing, just re-tap the document | Nothing — no data reached the verifier |

### 6.9 Tapping a stale or already-used link

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Tap an `av://` link whose session has already expired or was already used | That transaction never gets a result | Refused up front, blocking popup, no read attempted | Transaction stays pending/expired — never receives a verdict |

### 6.10 Reinstalling the app

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Uninstall, then reinstall the scanner app | Unaffected until next scan | Fresh install: no saved device keys, no per-origin threshold lock, empty log | Nothing yet |
| 2 | Scan the same document at a site where you had already registered (tier B) | **"Not allowed — key mismatch"** (`ok=true allowed=false reason=attester_key_mismatch`) | **"Delivered — the website shows the result."** — same document, new PIN prompt, but the phone's device key is brand new (Keystore state is per-install) | **Device-confirmed 2026-09-05:** the zktag computed from the document is identical to before (it's derived from the chip, not stored on the phone), but the site's original binding was to the OLD key, so this presentation is refused rather than being silently re-recognised — there is no re-enrolment path today (open question) |

Both readings are correct at once, and neither is a bug: the app's dialog reports delivery only
("your presentation reached the verifier"), while the page reports the verifier's separate verdict
("what it decided to do with what it received") — see "Two answers, two places" above.

### 6.11 Paste-link fallback

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Copy the handoff link/QR text instead of tapping it | Same link also usable this way | Paste field on the scan pane accepts the link, applies it | Same as tapping the link directly |
| 2 | Paste while a scan is already locked/in progress | — | **Expected (not yet verified):** the current field is the documented fallback-of-the-fallback for the cross-device QR path; exact behaviour while a read is in progress is not device-confirmed for the field as it exists today (a dedicated "Paste link" button with a locked/disabled state is designed but not yet built — see §12) | — |

### 6.12 QR cross-device path

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Open the page on one device (e.g. a laptop) | Renders the handoff link as a QR image, alongside the text link | — | — |
| 2 | Scan that QR with any ordinary camera app on your phone | — | The camera app's own link opens straight into the scanner app — no QR/camera feature built into the scanner itself | — |
| 3 | Continue as in 6.1/6.3 | — | Same flow as a same-device tap | Same as a same-device tap |

Device-confirmed 2026-09-03: this route worked twice via the stock Pixel Camera app scanning a
verifier-rendered QR image.

## 7. Operator's choice of attestation

What "attestation" means here, and how much of it an operator can change:

| Choice | Detail |
|---|---|
| Evidence is pluggable, not fixed | An operator can require any registered evidence type; the reference default (below) is a choice, not the only option |
| Reference default | A signed device key — `sig-ed25519/1` or `sig-p256/1` — the phone uses whichever it can produce, the verifier accepts either |
| Algorithm agility | The phone tries its strongest available algorithm first and only falls back on failure, never chooses to downgrade voluntarily |
| Ed25519 availability | Not guaranteed — on the Pixel 6a, the phone's own hardware keystore silently substitutes P-256 when Ed25519 is requested; the app reports which algorithm was actually used |
| Play Integrity | Exists in the design as one possible plug, never the default — the reference scanner ships without it (bare mode) |
| Key scope | One device key per (site, zktag) pair, not one key per site — isolates different documents at the same site from each other |
| Key binding | Trust-on-first-sight: the first presentation for a given (site, zktag) pair binds its key; a later mismatch is refused, never silently re-bound |
| Store choice | The demo uses one flat JSON file as its persistent store; a real deployment supplies its own; zkagent's own code stores nothing |
| Fail-closed | No store is ever allowed to fall back to an in-memory-only mode once running for real — a broken store refuses, it does not silently forget |

## 8. Default signing and trust

What's actually checked, and what isn't yet:

| Item | Detail |
|---|---|
| Request objects | Signed (ES256) by the site's own verifier before the phone will trust anything inside them; the app verifies this signature and refuses outright on any failure, never warns-and-continues |
| Which sites are trusted | The trust anchor is the requesting site's own web address itself (fetched from a well-known path over HTTPS in a real deployment); the current local demo/spike, running over plain HTTP, uses one built-in development-only key instead |
| Client identity today | Only the attester-key binding (tier B) proves "this same phone came back" — there is no check of the app's own package name or signing certificate on the wire yet |
| Client identity, planned | Pinning the scanner app's package name and signing certificate as the one accepted client identity needs a device-attestation token (Play Integrity / Key Attestation) — the OpenID4VP wire itself carries no such field. Moved out of the current demo to a future attestation-plug item (D78) — flagged as a disclosed limit, not silently added |
| Play Store complication | If/when this app is distributed through the Play Store, Google re-signs it, so the Play-distributed build's certificate is different from a locally-built one — any future pinning has to account for both |
| Masterlist | The full government certificate list is bundled in the app and its own signature chain is checked before it is trusted; a corrupt or unparseable list means the app refuses to give any answer at all (not a "no"); a well-formed list that's simply missing one issuer's certificate is a real "no," not an error |

## 9. What we chose and why

| Decision | Choice | Consequence for you | D# |
|---|---|---|---|
| Trust root | Government chip signature + phone hardware attestation, no ZK circuits of ours | No account, no issuer — the chip does the proving | D1 |
| Two tiers shipped, third not yet | Tier A and B only; tier C parked | A site can ask "are you over 18" or "have I seen you before," never your name or birthdate today | D19, D73 |
| Key/zktag isolation | Scoped per (site, document), not per site alone | Two different documents at one site, or one document at two sites, are never linkable to each other | D38, D39 |
| Mode A ships bare | No signature, no key, no PIN | Fastest, most private flow, but nothing to recognise you with next time | D27 |
| Mode B evidence | Signed device key, phone picks its strongest algorithm | PIN/biometric required every mint; real hardware-backed key | D30, D31, D36 |
| First-sight key binding | Trust-on-first-use, no re-enrolment mechanism | If you lose or reset the phone, a site you'd already registered at may treat you as a mismatch, not a stranger — see §6.10 | D38, Q31 (open) |
| Date coarsening | `current_date` rounded to midnight UTC | Removes second-level timing correlation across sites | D28 |
| Failure/success dialogs | Always a blocking popup with OK, never a fading toast | You never miss an outcome that needs your attention | D43, D52 |
| Scan log | Persists across app close, capped at 20 entries, clearable | Past scans reviewable later on this phone only, in plain language | D59, D70(b) |
| Fixed threshold | 18 only, no picker, in the current demo | Nobody can probe your exact birthdate by asking a changing set of ages | D74 |
| Demo hosting | Fixed to `127.0.0.1` over USB (`adb reverse`), not LAN/HTTPS | The "test right away" recipe is USB-only for now, not Wi-Fi | D76 |
| Tier-A "differs" column, final wording | Only the nonce and the challenge's issued-at/expires-at timestamps may ever differ; no signature row exists | Confirms tier A stays unsigned and indistinguishable, on purpose | D77 |
| Client trust list (FR10) | Moved out of the current demo to a future attestation-plug item, not built with a scanner wire change | Today's demo recognises a returning phone only via its tier-B attester key, not its app identity | D78 |
| Storage ownership | The demo runs its own store; zkagent code stores nothing | Even the demo you're testing keeps its own separate records — zkagent hosts nothing anywhere | D3 |

## 10. Limits and non-goals

| Limitation | Status |
|---|---|
| Clone replay on non-chip-authenticated documents | Disclosed, not mitigated — a cloned chip mints the same zktag as the real one when `chip_auth` is false |
| Not zero-knowledge | Never marketed as such — third-party ZK only enters as a validation-grade, tier-A-only evidence option |
| Tier C (name/expiry/other fields) | Not built — planned as M3b, gated on further PRD decisions |
| Play Store / HTTPS reachability | Not solved yet — a Play-installed (non-sideloaded) phone has no route to a verifier over HTTPS today |
| Online hosting | Out of scope for the current demo — operator runs it locally, USB only |
| Per-install state | Keys, zktag bindings' local record, threshold locks, and the log all reset on reinstall (see §6.10) |
| LAN cleartext | Refused outright by the app's network settings — no plain HTTP over Wi-Fi, ever |
| Key-loss recovery | Unsolved — no way to re-enrol at a site after losing the original device/key |
| App can't report the site's decision | The app cannot tell you the site's decision; read the page — see "Two answers, two places" |

## 11. How to test locally

Commands live in `apps/demo/README.md` — this is the human checklist only.

1. Install adb and enable USB debugging on the phone.
2. Confirm the phone is listed as a connected device.
3. Sideload the scanner app's debug build onto the phone.
4. Set up the USB port-forward from the phone to this machine.
5. Install and start the local demo server on this machine.
6. Open the demo page in the phone's own browser, at the forwarded address.
7. Tap one of the two buttons on the page.
8. Tap the resulting link on the phone (or, testing the cross-device path, scan its QR code with a second phone's ordinary camera app).
9. Tap your document to the phone when prompted.

**What to expect at each step:** the device list shows exactly one phone (step 2); the demo page
loads with two buttons and no visible errors (step 6); tapping a button shows a waiting state with
a link and a QR code (step 7); tapping the link or scanning the QR opens the scanner app directly,
with no extra app chooser (step 8); the app shows a waiting state ("Tap and scan" / "Tap and
verify"), then a blocking outcome popup once you tap the document (step 9); the page updates to an
allowed / refused / already-registered result on its own, with no manual page refresh needed.

**If the phone disappears from adb mid-session:** the port-forward from step 4 does not survive a
USB disconnect — reconnect the cable, confirm the phone is listed again, then redo step 4 before
continuing.

**To reset the demo:** stop the server and delete its store file; the next start creates a fresh,
empty one.

**What "already registered" means after a reset:** nothing — every zktag/key binding is forgotten
along with the store file. A document that was previously refused as a repeat will mint fresh
again on the next scan, exactly like a genuine first-time visit.

## 12. Data handling rules

| | What it holds |
|---|---|
| The app itself | Nothing from the document — no MRZ field, no date of birth, no name. Only its own device key(s) and a value-free scan log |
| The site (verifier/demo) | Spent one-time nonces, attester-key bindings, and zktags-seen — never document data, because none is ever sent to it |
| Never in logs or screenshots | Any MRZ field, name, date of birth, document number, raw zktag, nonce, public key, or signature value — records use field names, boolean outcomes, and truncated identifiers only |

## 13. Planned, not built

| Item | What it will do | Status |
|---|---|---|
| S1 | Fixed preset threshold list (15/16/18/21/60/65), locked per site on first ask, named exceptions (§5) | Planned, scanner-side |
| S2 | Shows the exact question ("This website asks if you are over 18") on-screen before you tap the scan button (§5) | Planned, scanner-side |
| S3 | Scan-pane cleanup: dedicated "Paste link" button, dimmed and disabled mid-scan instead of today's plain field; below the button, a one-line "Last scan: `<site>`, over `<age>`: true/false, delivered ✓/refused ✗" summary replaces the full report view (D79) | Planned, scanner-side |
| M3b | Tier C: attributed disclosure (e.g. name match, expiry-bucket booleans), pinned to approved issuers only | Not started — blocked on several open design questions |
| Play Store track | Closed-testing upload of the scanner app; a showcase track only, doesn't change how the demo is reached | Not started — opens after a separate proof-of-concept passes |

---

*Sourced from `docs/product/zkagent-prd.md`, `docs/wiki/decisions.md`, `docs/wiki/milestones.md`,
`docs/wiki/questions.md`, `docs/logs/M3-POC-EVIDENCE-2026-09-04.md`,
`docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`, `docs/logs/M2-SESSION-POC.md`,
`docs/logs/M2-DEVICE-EVIDENCE.md`, `apps/demo/README.md`, `apps/scanner/README.md`. Run recipe:
`apps/demo/README.md`.*
