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
| The app's popup | Delivery status: did the phone read the chip and get its presentation to the verifier | "Read OK", "Sent", "Refused by the app" (a local check failed before anything was sent), "The website did not accept the answer — reopen the link from the site." (the verifier answered with a non-2xx status, any redirect included; the status and the verifier's error label appear only in the Log), "This link was already used — reopen the link from the site." (HTTP 409 already_responded) |
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
| Tier C | Attributed mode (yes/no answers about expiry, name, date of birth, nationality, document number): built and device-checked; shipped in 0.9.0 (D109, D116). Off by default. See §4. |
| zktag | The per-site tag tier B adds. Computed on the phone from the document and the site's address; the same document at the same site always makes the same zktag; the same document at a different site makes a different, unrelated zktag. |
| Verifier | The website's own server that checks what the phone sends and decides allow/refuse. Every operator runs their own; zkagent runs none. |
| Handoff link | The `av://` link the page hands to the phone (tap on the same device, or scan a QR code from a second device) to start a scan tied to that specific request. The scanner app only handles `av://` links today — it has no `https` App Link intent filter — so the demo must be run with the `av` link scheme for the same-device tap to work; the demo's `https` default is only a placeholder for a possible future app-link path, not something the scanner can open yet. |
| QR path | Scanning that same link with any ordinary camera app on a second phone — the scanner app has no built-in camera/QR feature of its own. |
| Attester key | A key the phone generates in its secure hardware (Keystore/StrongBox) and uses to sign a tier-B answer, so the site can trust it came from this phone. Requires a PIN/biometric approval each time it signs. |
| Chip authentication | A property some (not all) chips support that proves the physical chip is genuine, not a copy. Reported as true/false at tiers B/C only — tier A never emits it. |
| Masterlist | The public list of government signing certificates the phone checks the chip's signature against. Bundled in the app, verified before use. |
| Threshold | The age number being asked about (e.g. "over 18"). Fixed at 18 on the demo page; the app enforces a small published list of allowed numbers (§5). |
| Origin | The website address (scheme+host+port) the request came from. Keys, zktags, and thresholds are all scoped per origin. |
| Store | The website's own persistent record of zktags/keys it has seen. zkagent code stores nothing; the demo keeps a plain JSON file as an example. |

## 3. Tier comparison

| | Tier A (anonymous) | Tier B (pseudonymous) | Tier C (attributed) |
|---|---|---|---|
| What the site learns | One yes/no bit | Same bit, plus "have I seen this document at my site before" | A yes/no answer per question it asked, from the five kinds in §4; never the field itself |
| What the phone sends | Bare answer, no signature, no key | Signed answer + zktag, from a device key | The yes/no answers for exactly the questions asked, echoing only the question's name and fixed boundary; no zktag, no device key |
| PIN/biometric prompt | None | Yes, before every mint | Your confirmation on the phone before each scan, which shows the questions |
| Scanner popup | Yes, every scan — confirmed, including two tier-A scans in a row | Yes, expected every scan — **unconfirmed observation: may not appear on a repeat tier-B scan** (an earlier report of a missing popup was traced to tier-B taps, not tier A; corrected 2026-09-04) | Yes: an outcome popup, or a red refusal popup |
| Page outcome text | "ALLOWED (over threshold)" or refusal reason | First visit: allowed/refused as normal. Repeat visit, same document: "Already registered at this site" | The site's own page shows the answers it received, or a generic "refused" |
| Where the verdict shows | Page, never the app (see "Two answers, two places" above) | Page, never the app (see "Two answers, two places" above) | Page, never the app (see "Two answers, two places" above) |
| Stored on the phone | Nothing persists beyond a value-free log entry | Same, plus a device key bound to (this site, this zktag) | Nothing persists beyond a value-free log entry |
| Stored by the site | Nothing durable (a spent one-time nonce) | zktag + attester-key binding, kept until the operator clears it | Whatever the site chooses to keep of the answers; nothing is held by zkagent |
| Can the site recognise you again | No | Yes, at that one site only | No tag is sent, but a "yes" to a document-number question links you to a number the site already had (§4.1) |
| Can two sites link you | No | No — zktags differ per site by design | Not by anything the phone sends; a shared name or document number is the sites' own matter (§4.1) |
| Works with a cloned chip | N/A (no chip check in tier A) | Yes, if `chip_auth` is false — a clone mints the identical zktag as the real holder | Yes, if `chip_auth` is false — a cloned chip answers as the real one would (§10) |
| Threshold | 18 (fixed) | 18 (fixed) | Date-of-birth question uses the site's one locked age (§5) |
| Status | Built and device-confirmed (M3, shipped v0.6.0). The client trust list (FR10) is not built: it needs an attestation plug (§6.5 S4, D78; backlog) | Built and device-confirmed (M3, shipped v0.6.0). The client trust list (FR10) is not built: it needs an attestation plug (§6.5 S4, D78; backlog) | M3b — built and device-checked; shipped in 0.9.0 (D109, D116) |

## 4. Tier rules — what each may and may never ask

| Tier | May ask | May never ask | Gating |
|---|---|---|---|
| A | One yes/no threshold bit | Any identifying field; anything that could stably identify you | None — always available, the default |
| B | Tier A's bit, plus "have I seen this document at my site before" (the zktag) | Your name, birthdate, or any document field; anything that links you across two different sites | Must be explicitly requested by the site — never inferred or defaulted |
| C | Booleans over identifying fields, from a published verb list (the vocabulary was ruled in D90: expiry buckets, name-match, date-of-birth range, nationality, document-number match — all five are built) | Similarity scores, ever; any predicate off the published list | Refused outright unless the operator turned tier C on, the requesting site's exact hostname is on the build's pin list, and you turned on "Allow additional verifications" (D109(h)); this is M3b (`docs/wiki/milestones.md` §6.9), built and device-checked; shipped in 0.9.0 |

**Tier C (built and device-checked; shipped in 0.9.0; D109, D116).**
Under the design (D109(h), `docs/wiki/milestones.md` §6.9) a tier-C request is answered
only if three things are all true: the operator turned tier C on in the build, the site's exact
hostname is on that build's pin list (an unlisted site is refused before anything is read from your
document), and you turned on the app setting **"Allow additional verifications"**, which is OFF by
default. With the setting off the app answers age verification (tiers A and B) only. With it on,
every pinned site may ask any of the five questions (expiry, name match, date-of-birth range,
nationality, document-number match), as yes/no answers (a name-match question carries given names
and surname as two separate fields, so there is no order to get wrong; given names must match in
full as on your document, and a very long name that your document's machine-readable line cut
short may read as "no match"); the number itself and your other fields are
never sent. Before you confirm each scan the app shows the questions being asked, read from the
site's signed request. The app answers every question in a request or refuses the whole request,
never part of it, and a refusal tells the site only "refused". Age verification (tiers A and B) is
open to any site in a build whose `verifiers` is `"any"` (D109(i)); everything else here is gated behind the
operator's list and your setting. A site gets **one age in total**: whichever age it asks about first,
through an age check or through tier C's date-of-birth question, is the only one it can ever use, and
a different one is refused (D109(i)3). The limits are in the next subsection, stated plainly.

### 4.1 Limits of tier C and open age verification, stated plainly

Nothing below is hidden by the design and none of it is fixed; each is a disclosed limit.

**Who can ask what**

- **Any site can ask an age check, if the build is open.** In a build whose `verifiers` is `"any"`
  (the showcase build is one), any website can ask an age check (tiers A and B). What still protects
  you: each site is locked to the first age it asks about (§5), the request is signed and tied to the
  site's own address, and every scan needs your own confirmation on the phone, which shows the site and
  the question. No list limits which sites may ask; an operator who wants a list uses the exact-list form.
- **Tier C is not open.** It stays behind three things together: the operator's tier-C switch, an exact
  hostname on the operator's pin list, and your own setting "Allow additional verifications", which is
  OFF by default. An operator who built the app with tier C off, or with an empty list (the showcase
  build is one), answers no tier-C question at all.
- **Many addresses, many ages.** The age lock is per website address. A party that runs many addresses
  can ask a different age on each and combine the answers. This is more exposed in an open build, where
  no list limits which addresses may ask. Not covered, not prevented.

**What a pinned site can still learn**

- **Repeat questions.** Nothing limits how often an approved site asks. It can ask the same
  questions on later scans and keep confirming guesses of a name, a document number or a nationality
  until one is right. Your setting is the brake: turn it off and tier C is never answered.
  Within a single request the app refuses a second nationality question of the same kind (one "is it
  X?" and one "is it none of these?" at most), so one scan cannot cover every country but one. It
  likewise refuses a second document-number question in one request, so one scan tests one guess.
- **Expiry date.** The 3, 6 and 12 month expiry questions are three different questions and are not
  locked, so a site can combine them to narrow down your document's expiry date. That is not prevented.
- **Age.** Age narrowing is prevented: a site gets one age in total, whichever it asks first, through an
  age check or through tier C's date-of-birth question, and a different one is refused. **One exception,
  said plainly:** a site the operator names in `multi_threshold_verifiers` is exempt from the lock and CAN
  ask several ages, so it can narrow your age down. That is the operator's explicit grant. The showcase
  build names no such site.
- **One bit about your date of birth.** Even with the lock, the one age a site gets is still one bit
  about your date of birth (over or under that age). Nothing removes it.
- **Document number.** A "yes" to a document-number question tells the site that you are the person
  behind a number it already had. The number is not extracted from your phone, but that is a real link
  between you and what the site already knew.
- **A very long name.** A very long name may read as "no match" even when you typed it correctly: the
  document's machine-readable line cuts long names short, and given names must match in full. A "no match"
  is therefore not proof that the name differs.
- **A refusal tells the site only "refused".** An approved (pinned) site that is refused learns that the
  request was refused, never why (the setting being off, a question the app does not answer, an age
  conflict and the rest all look the same to it). It can tell a refusal from you walking away.
  The reasons show in the app only.
- **Unapproved sites still learn the link was opened.** In an open build the app cannot know a request
  is tier C until it has fetched it. So even for a site that is not on the pin list, the app first
  fetches that site's signing key and the request itself (two requests to that site), and the site
  learns that the link was opened and from which network address. The app then refuses before anything
  is read from your document, and sends that site no reply.

**What the pin does and does not mean**

- **The pin is by hostname only.** It trusts whoever controls that hostname's DNS and TLS, because the
  site's signing key is fetched from the hostname itself. A hijacked or lapsed hostname passes the gate.
  There is no key pin.
- **The list changes only with a new build.** A site added to or dropped from the pin list reaches you
  only when you install a newer build. There is no auto-update and no remote revocation, so an older
  install keeps its old list for as long as you keep it.
- **No authority stands behind the list.** Nothing vouches that a pinned site is legitimate beyond the
  operator having put it there.

**What was tested**

- **Real-internet key discovery was run once.** Fetching a site's signing key over real HTTPS was run
  once, against one real host, in a test (chunk 3, check 23(d), `docs/logs/M3B-CHUNK3-EVIDENCE-2026-10-27.md`):
  a good key answered, a mismatched key was refused and an untrusted certificate was refused. Wide
  real-world interop beyond that one host is untested. The key fetch follows no redirects at all: any
  redirect counts as "key not found" and the request is refused (D113). Since 0.9.x (#52) the app also follows no
  redirects on the `request_uri` fetch and on the answer or refusal POST to `response_uri`: a 3xx is treated as
  not delivered (or, on the fetch, as "request could not be verified"). Operators must serve `request_uri` and
  `response_uri` at their final URL, with no http-to-https, apex-to-www or trailing-slash redirects.

**Your setting**

- **Reinstalling turns it off again.** "Allow additional verifications" is stored only on this install,
  so it returns to OFF if you uninstall and reinstall the app (the same as the age locks, §6.10).
  The same words are in the app next to the setting.
- **Chip cloning.** See §10: a cloned chip on a document without chip authentication is not detected.


## 5. Preset age thresholds and per-site locking

The published policy for which ages a site may ask about, and how it's locked once asked:

| Rule | Detail |
|---|---|
| Supported thresholds | A fixed, published set of allowed ages: 15, 16, 18, 21, 60, 65. A site must request exactly one of these; anything else is refused loudly, no scan sent |
| One threshold per site | A site gets one threshold per hostname, locked the first time it's seen (first-seen). A later request from that SAME site for a DIFFERENT threshold is refused on the phone — the site learns nothing about why |
| Two thresholds, two hostnames | A site that legitimately needs two different thresholds must use two separate hostnames — one threshold per hostname is the rule, with no exception by default |
| Named exceptions | A small, app-side allowlist of exact website addresses (no wildcards) permitted to ask more than one threshold at a single hostname — membership is the app's own decision, never the site's; ships empty today |
| Pre-tap question | Before you tap the scan/verify button, the app shows the exact question being asked (e.g. "This website asks if you are over 18"), read from the site's signed request, never a generic string |
| Shared with tier C (D109(i)3; built and device-checked; shipped in 0.9.0) | The lock is ONE age per site across tiers: if a site first asks about 18 through an age check, its tier-C date-of-birth question can only ever use 18, and the other way round; a different age is refused (for tier C, the whole request is refused). A site the operator names in `multi_threshold_verifiers` is exempt (D109(j)2) and may use several ages, which lets it narrow your age down; the reference build names none. Expiry questions are not part of this lock |
| Reinstalling resets it | Reinstalling the app resets the per-site lock along with everything else in §10 — a fresh install remembers nothing |
| Today | The current demo requests age 18 by default; a test-only override lets an operator try the other preset values (and one deliberately-unsupported value) for testing, without changing the page's own default |

**Status: built and device-confirmed, 2026-09-05** (scanner-side item S1;
`docs/logs/M3-SCANNER-S1-EVIDENCE-2026-09-05.md`).

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

### 6.13 Tapping a broken or truncated link

| Step | You do | Page shows | App shows | Verifier logs |
|---|---|---|---|---|
| 1 | Open an `av://` link that is cut off or malformed (for example, missing its `request_uri`) | That transaction never gets a result | The app opens (or comes to the front) with a short, non-blocking notice: "That link looks broken — nothing was scanned." No popup, no entry in the app's Log tab, no read attempted; nothing about any session already in progress changes | Nothing — the link never reached the verifier |

Device-confirmed 2026-10-24 (finding #23 / D87, Pixel 6a): the notice appeared both when the app was
cold-started by the link and when it was already open. A link with the wrong host never reaches the
app at all (the manifest filter only accepts host `authorize`).

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

## 7.1 Building and signing your own release (per-operator recipe)

zkagent is distributed as one upstream Apache-2.0 repository, not as a hosted service (see §7):
each operator builds and signs their own copy of the scanner app, under their own package
identity, with their own keystore. There is no shared zkagent signing key, and there never will
be — the owner's own showcase build (a signed APK on GitHub Releases; the Play track is parked, D83) uses a separate keystore of
its own, generated the same way as below.

**Why this matters.** Android requires every APK to carry a certificate, and the SHA-256 digest
of that certificate is the app's identity — the "package + cert digest" pair that FR10/D17 has a
verifier's trust list pin under §6.5 S4. One keystore holds one key pair for the life of your
package: every update you ship must be signed by the same key, or Android refuses the install,
and if you lose the key, your app can never be updated again under that package name — only
replaced under a new one, which every existing verifier trust-list entry would then need to
re-pin. Generate it once, back it up offline, and treat it as the one truly un-losable asset in
this whole recipe.

1. **Generate the keystore.** EC P-256, in a PKCS12 container, stored under the already-gitignored
   `secrets/` directory (`.gitignore` line 13):

   ```
   mkdir -p secrets
   keytool -genkeypair -v -keystore secrets/<name>.p12 -storetype PKCS12 \
     -alias <alias> -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
     -validity 10950 -dname "CN=<operator>"
   ```

   `keytool` prompts for the keystore password interactively — type it there; never pass a
   password on the command line, where it would land in shell history and process listings.
   **Gotcha for agent-driven shells**: this step needs a real interactive terminal for the
   passphrase prompts — an agent's own non-interactive shell can't supply them (it will hand
   `keytool` empty input and get "Too many failures"); run this step yourself, directly.

2. **Read the certificate digest.** This digest is public — it's an identity, not a secret — and
   is what goes into a verifier's trust list and your own evidence log:

   ```
   keytool -list -v -keystore secrets/<name>.p12 -alias <alias> | grep -A1 'SHA256'
   ```

   The keystore file and its passwords, by contrast, are secrets: never commit them, never put
   them in CI logs.

3. **Build a signed release.** From `apps/scanner`, feed the keystore to the build only through
   the existing environment variables — `apps/scanner/app/build.gradle.kts`'s `signingConfigs`
   reads these and is otherwise unchanged:

   ```
   KEYSTORE_FILE=$PWD/secrets/<name>.p12 KEYSTORE_PASSWORD=… KEY_ALIAS=<alias> KEY_PASSWORD=… \
     ./gradlew assembleRegularRelease
   ```

   You may need `JAVA_HOME` pointed at a JDK 17 for this — Gradle's own provisioned one under
   `~/.gradle/jdks/` works. Output lands at
   `app/build/outputs/apk/regular/release/app-regular-release.apk`. **Without `KEYSTORE_FILE` set,
   the build emits `app-regular-release-unsigned.apk` instead — that's by design, not an error:**
   it's how every build before a keystore existed produced only an unsigned APK.

4. **Verify the signature and digest independently of the keystore**, straight off the built APK,
   using `apksigner` from the Android SDK build-tools (path pattern
   `$ANDROID_HOME/build-tools/<version>/apksigner`):

   ```
   apksigner verify --print-certs app-regular-release.apk
   ```

   Confirm the SHA-256 it prints matches step 2's reading. Note the format difference: `keytool`
   prints the digest colon-separated uppercase hex (`1F:6B:CE:…`), `apksigner` prints it as
   unbroken lowercase hex (`1f6bce…`) — same 32 bytes, same digest, just two conventional
   renderings; don't read a format mismatch as a verification failure.

5. **Back up and keep it out of git.** Keep an offline backup of the `.p12` file and its
   passwords — there is no recovery path if both are lost. Confirm the file is actually ignored
   before trusting that: `git check-ignore -v secrets/<name>.p12`. Never let the keystore or its
   passwords appear in a CI environment log. Rotating this key is not a maintenance step — it
   creates a new app identity, so don't do it unless you mean to.

6. **If you upload to Google Play (the owner's own Play track is parked, D83):** Play App Signing re-signs your APK with Google's own key —
   your local key becomes only the "upload key" (see §6.6 item 2 in `docs/wiki/milestones.md`
   for the owner's own showcase-listing deliverables, which are not required of operators). Any
   verifier pinning your Play-distributed build must pin Google's digest instead of (or alongside)
   your local one — record that digest only after a real upload, never guess it in advance.

7. **Where the digest goes.** Your own verifier's trust list (§6.5 S4's pin), and your own
   evidence log. For the owner's own showcase build specifically, the digest is recorded in
   `docs/logs/` and the PRD once that build exists — not part of this generic recipe.

This local release-signing key, a Play upload key (if you list on Play), and the device's own
`AndroidKeyStore` attester keys (D38) are three different keys for three different purposes —
keep that distinction; see PRD §6.6 item 7 and `docs/wiki/decisions.md` D38/D80/D81.

## 7.2 Operator config (`operator.json`)

What it is: `apps/scanner/operator.json`, one JSON file at the module root, next to
`apps/scanner/README.md`. It is how an operator adjusts the knobs listed in §7 above without
forking or editing source (D81, D82) — the file, not the code, carries your verifier allowlist,
your threshold list, and your app's display name.

**Build-time only, never fetched at runtime.** Gradle reads and validates the file when you run a
build (`app/build.gradle.kts`, configure time), and bakes the validated values into
`BuildConfig`/`R.string` constants inside the APK. The app never re-reads the file, and never
fetches it — or anything like it — from a server while running. This is deliberate: a
runtime-fetched config would reintroduce a hosted dependency and a remotely-controllable
allowlist, which is exactly what NO-GO #3 rules out. Changing a knob means editing the file and
rebuilding; there is no over-the-air knob update.

**Knobs, allowed values, and what a bad value does:**

| Knob | Allowed values | If wrong |
|---|---|---|
| `schema_version` | Must be `1` | Build fails, names the missing/wrong value |
| `operator` | Required top-level object: `{ "name": "<string>", "contact_url": "<string>" }` — no other keys | Build fails if `operator` isn't an object, if `name` or `contact_url` isn't a string, or if any other key appears under `operator` |
| `tiers` | `"A+B"` or `"B"` (exact string) | Build fails |
| `thresholds` | Non-empty subset of the fixed, published list `{15, 16, 18, 21, 60, 65}` (D74) | Build fails, names the offending value — you cannot add a threshold outside this list |
| `verifiers` | Either a non-empty array of bare hostnames (no scheme, no port, no wildcard), or exactly the single lower-case string `"any"`, which opens age verification to any site (built, D109(i)). The two forms are mutually exclusive | Build fails, names the bad value. A bad array entry (e.g. a wildcard or a `host:port` string), or an empty array, fails as rule 4. Any other value fails as rule 12: `"*"`, `"ANY"`, `"any "`, an empty string, a number, an object, `null`, or an array that contains the entry `"any"` (`["any"]` is not the open form) |
| `multi_threshold_verifiers` | Array of bare hostnames, same shape rule as `verifiers`; default `[]` | Build fails on the same shape violations as `verifiers` |
| `evidence_plug` | `"none"` only, today | Build fails if it names anything else — no attestation plug ships in this repo yet |
| `tier_c_verifiers` | Array of bare hostnames, same shape rule as `verifiers` (no scheme, no port, no wildcard); default `[]`. It is the exact-hostname pin list for tier C (built and device-checked; shipped in 0.9.0, D109), and works together with the switch `"tier_c": { "enabled": true }` (default off) | Build fails on a bad entry (rule 7). Build fails if the switch is on and the list is empty (rule 10), or the list is non-empty and the switch is off or absent (rule 11). An unknown key under `tier_c` fails as rule 8. The showcase build leaves both off |
| `strings.app_name` | Any string | N/A — this is the only free-text display-name knob (`operator.name`/`operator.contact_url` are also unrestricted strings, but identify the operator rather than the app) |
| any unknown key, at the top level or under `strings` | — | Build fails (typo/scope-creep protection; nothing is silently ignored) |
| the file itself | Must exist | Build fails if missing, unless you're building the repo's own committed reference config |

**Verifier hostnames.** `verifiers` is your allowlist of sites the scanner will accept a request
from — hostnames only, matching the app's existing D74/D38 host-only convention: no `https://`,
no port. The local M3 dev origin `http://127.0.0.1:8787` is written as `"127.0.0.1"` in this
field. If a site isn't on this list, the app refuses the request before doing anything else — see
below for exactly what that looks like.

*Open age verification (built, D109(i)).* `verifiers` can instead be the single string `"any"`, and
then a build answers an age check (tiers A and B, for the thresholds you listed) from any website,
with no hostname list. The repo's showcase build uses it. The exact-list form stays valid if you
want a closed build. `"any"` does
not open tier C: that stays behind `tier_c_verifiers`, the build's tier-C switch and the holder
setting. What still protects you with `"any"`: each site is locked to the first age it ever asks about
(§5), the request is signed and tied to the site's address, and every scan needs your own
confirmation on the phone, which shows the site and the question. Tested once only: fetching a
website's signing key over real HTTPS was run against one real host in a test (a good key answered, a
mismatched key and an untrusted certificate were refused); wide real-world interop is untested, and the
key fetch follows no redirects. See §4.1.

**`multi_threshold_verifiers`.** This is D74 rule 2's named-exceptions list, exposed as a knob —
the same list `ThresholdPolicy.NAMED_EXCEPTIONS` already carries in code, ships empty by default.
Under the default policy, a site locks its threshold on the first question it ever asks and is
refused if it later asks a different one (D74 rule 1); listing a hostname here is how an operator
grants that one site permission to ask more than one threshold. It is not a general escape hatch —
membership is the operator's call, never the site's own.

**`tier_c_verifiers` and `tier_c`.** Tier C is built and device-checked; shipped in 0.9.0 (D109). It is off in the showcase build.
To turn it on, set `"tier_c": { "enabled": true }` and list the exact hostnames of the sites allowed to ask
tier C in `tier_c_verifiers` (a pin by hostname only, no key pin, checked offline and baked into the build).
The two must agree: the switch on with an empty list, or a list with the switch off, fails the build
(rules 10 and 11). A site not on the list is refused before anything is read from the document, and
even a listed site is answered only if the holder has turned on "Allow additional verifications".
`"verifiers": "any"` never widens this list. Limits: a pin is only as strong as the hostname's DNS and
TLS; the list changes only when you rebuild and a holder installs the new build (no remote revocation);
there is no authority behind the list; and with no per-site caps, a pinned site can repeat requests
and narrow facts down over time (see §4.1).

**`operator`.** Two required strings, `name` and `contact_url`, identifying who operates this
build. Gradle validates that `operator` is an object, that both fields are strings, and that no
other key appears under it (rule 8), then bakes them into `BuildConfig.OPERATOR_NAME` and
`BuildConfig.OPERATOR_CONTACT_URL`. As of this writing nothing in the app reads either constant —
they exist for the operator's own records and for a future support/attribution surface, not
today's UI.

**`strings.app_name` is the only string knob that changes app behavior.** You can rename the app; you cannot change the
question the app asks before a scan. The disclosure sentence ("This website asks if you are over
N") is computed in code, not read from this file, in every schema version — D74 rule 3 makes its
exact wording a user-protection requirement, not branding, so it stays out of operator control by
design.

**Signing is not in this file.** Your release keystore is a separate build input, supplied via
environment variables (§7.1) — `operator.json` never references or contains key material.

**Sanity-checking your file.** `apps/scanner/scripts/operator-json-negative.sh` runs a set of
deliberately-bad `operator.json` variants against `:app:assembleRegularDebug` and confirms each
one fails the build while naming the violated rule, then restores your real file and confirms a
clean build succeeds again. Run it after editing the validator itself, or any time you want to
confirm the build-fails-closed behavior on your own machine.

**What a refused site's users see.** If a site isn't in `verifiers` (this cannot happen in a build whose `verifiers` is `"any"`: every site passes this check, and tier C is refused for an unpinned site by a different rule, see above), the app shows an in-app
refusal naming the unlisted hostname — before any read or mint happens. The site itself receives
no request at all; it has no way to tell the difference between "the user declined" and "the
operator's allowlist doesn't include me."

**Verifier-side (`apps/demo`) config is a later round.** This file only configures the scanner
app. The demo verifier's own knobs (link scheme, threshold, scope domain, signer keys) are still
env-var driven and untouched by this schema — deferred to a later round (D84 point 4).

See `docs/wiki/milestones.md` §6.7 for the full schema and the nine build-time validation rules,
and `docs/wiki/decisions.md` D74, D81, D82, D84 for the decisions behind them.

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
| Third tier gated | Tiers A and B are open to any site in a `"verifiers": "any"` build; tier C is built and device-checked; shipped in 0.9.0 and stays off unless the operator pins the site and you allow it | Without tier C, a site can ask "are you over 18" or "have I seen you before," never your name or birthdate | D19, D73, D109 |
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
| Tier C (name/expiry/other fields) | Built and device-checked; shipped in 0.9.0 (M3b, D109, D116); see §4.1 for the limits it carries, including no per-site caps |
| Play Store / HTTPS reachability | Moot — the Play track is parked (D83); distribution is GitHub Releases + sideload, and the demo is reached over `adb reverse` (D76) |
| Online hosting | Out of scope for the current demo — operator runs it locally, USB only |
| Per-install state | Keys, zktag bindings' local record, threshold locks, the "Allow additional verifications" setting (back to OFF), and the log all reset on reinstall (see §6.10, §4.1) |
| Tier-C and open-age limits | Repeat questions, expiry bracketing, the multi-age exception, hostname-only pins, no remote revocation and the rest are listed in §4.1 |
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

## 13. Not built (backlog)

The rows below are designed or noted but not built. Nothing here is scheduled. Each one, with its
reason and its decision pointers, is in [`docs/wiki/backlog.md`](../wiki/backlog.md), which also says how
to revive it. Anything revived goes into the PRD first (NO-GO #10). Tier C is not in this list: it is
described in §4 as built and device-checked, and shipped in 0.9.0 (D116).

| Item | What it would do | Status |
|---|---|---|
| Client trust list (§6.5 S4) | Pin the scanner's package and signing certificate via an attestation plug | Not built — needs an attestation plug |
| Play Store track | Closed-testing upload of the scanner app | Parked (D83) |

(§6.5 items S1, S2, and S3 — the preset threshold list/lock, the pre-tap question line, and the
scan-pane cleanup — are built and device-confirmed; see §5 above and §3's status row.)

---

*Sourced from `docs/product/zkagent-prd.md`, `docs/wiki/decisions.md`, `docs/wiki/milestones.md`,
`docs/wiki/questions.md`, `docs/logs/M3-POC-EVIDENCE-2026-09-04.md`,
`docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`, `docs/logs/M2-SESSION-POC.md`,
`docs/logs/M2-DEVICE-EVIDENCE.md`, `apps/demo/README.md`, `apps/scanner/README.md`. Run recipe:
`apps/demo/README.md`.*
