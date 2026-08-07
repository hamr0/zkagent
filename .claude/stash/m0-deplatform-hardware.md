# zkagent — M0 De-Platforming & Hardware Session Stash

> ⚠️ **SUPERSEDED IN PART — read `m0-hardware-android-first.md` (2026-07-31) first.**
>
> The hardware conclusions below are **wrong** and must not be acted on:
> - The **ACR122U cannot read ICAO e-documents** (261-byte APDU cap; no time-extension
>   propagation). The "werkt voor DigiD" signal cited below is about Dutch ID cards, not
>   ePassports. The correct part, if a reader is ever needed, is the **ACR1581U-C1**.
> - The bol.com listing referenced below (~€36.99) **no longer exists**.
> - The owner has since chosen **Android-first, phone instead of reader**. Note also that
>   the "fallback: physical NFC Android phone" below is under-specified — a Pixel 4a/3a
>   will *not* work past M0, because Play Integrity strong integrity requires a security
>   patch from the last 12 months.
>
> Everything else in this file (platform explainers, division of labour, agent delegation,
> PRD v1.1 edit log) remains valid.

**Timestamp:** 2026-07-30

## Session summary

Continued from the founding-session stash to answer "what's next." The session converged on de-platforming M0 — removing the Apple/iPhone dependency that was the prior session's stated blocker — and finding a path to unblock it without a Mac or paid Apple developer account. Along the way, several technical misconceptions the owner held about NFC/passport reading on phones and browsers were clarified and are worth keeping as reference explainers. The session ended with PRD v1.1 and CHANGELOG edits made (uncommitted), a resolved M0 hardware path (USB PC/SC reader on the owner's Linux desktop), and a concrete hardware shopping list for the Netherlands.

## Key decisions

- **M0 de-platformed.** Preferred path: USB PC/SC NFC reader (e.g. ACR122U) + JMRTD on the owner's Linux desktop. Fallback: physical NFC Android phone + JMRTD. iPhone variant is only revisited if a Mac + paid Apple account later materialize.
- **PRD bumped v1.0 → v1.1** (`docs/01-product/zkagent-prd.md`), edits uncommitted at session end:
  1. Header amendment note added.
  2. M0 row rewritten per the de-platformed path above.
  3. M1 App Attest fixtures made conditional: published Apple sample fixtures stand in if M0 is not iOS; real fixtures get re-verified at M2.
  4. Riskiest-assumption #2 reworded — the Apple entitlement no longer gates M0.
  5. Q2 (Apple NFC entitlement) resolved as moot for M0. Recorded for the record: Core NFC does need a paid Apple Developer Program account, but that's a standard, known capability once paid — not a new risk.
  6. New Q6 added (targeted for M2): Mac-less iOS build paths — either (a) paid Apple account + cloud Mac (MacinCloud/MacStadium) or CI-only build (Xcode Cloud, GitHub Actions macOS runners), or (b) flip D2 to Android-first (this option merges with/subsumes Q5). Decision deferred to M2 entry, to be made on evidence rather than now.
- **CHANGELOG updated:** "iPhone POC" language replaced with platform-neutral phrasing; the v1.1 amendment recorded under `[Unreleased]`.
- **README needed no change.**
- **Owner constraints confirmed and locked in:** no Mac, no paid Apple developer account. Host is Linux (Fedora). Owner is based in the Netherlands (relevant to the hardware search below).
- **D9 (derivation field) still explicitly open**, pending M0 evidence — unchanged from founding session, just reconfirmed.

## Doc/repo state

- `docs/01-product/zkagent-prd.md` — now v1.1 in the working tree (see edits above). **Uncommitted.**
- `CHANGELOG` — `[Unreleased]` entry added for the v1.1 amendment and the iPhone→platform-neutral wording fix. **Uncommitted.**
- `README` — unchanged this session.
- Commit policy: PRD + CHANGELOG edits are deliberately left uncommitted; commit only when the owner asks.
- No changes to npm placeholder or repo settings this session (MIT-mislicense issue from founding session is still open, still gated by NO-GO #8, still not urgent).

## Findings index

**NFC/passport-reading platform explainers (established this session, worth keeping as reference):**
- iPhone has NFC *hardware* but no built-in passport-reading feature.
- Browsers on **all** platforms cannot read passport chips: Web NFC is Chrome/Android-only and NDEF-only, while passports are ISO 7816 smartcards requiring a BAC/PACE APDU conversation — a different protocol layer entirely.
- iOS Shortcuts' NFC trigger is detect-only, not a usable loophole for chip reads.
- Bank "web" NFC verification flows are actually web → QR code / universal link → native app (or App Clip) handoff — architecturally the same design as zkagent PRD step 5, not a counterexample to the platform wall.
- App Clips flagged as a candidate for an install-free feel at M2/M3 — ID vendors like ReadID already use this pattern.
- The $99/yr Apple fee is a **publisher** fee: the Core NFC entitlement requires a paid Apple Developer Program membership; free/personal Apple developer teams cannot add it. End users are unaffected and never pay — they'd install the finished app free from the store.
- **Division of labor reaffirmed:** the vetted library (NFCPassportReader on iOS, JMRTD on desktop/Android) handles the chip protocol — MRZ-derived keys, BAC/PACE, secure messaging, DG parsing. zkagent's own code handles masterlist SOD verification, KDF→secret derivation, tag = HMAC(secret, domain), attestation, handoff, and the verifier SDK.
- **Nothing is "extracted" from the chip.** Passport chips never export private keys. The secret is *computed* from stable chip data — the exact field (D9) is still open pending M0 evidence.
- **Agent delegation reaffirmed:** agents receive a delegation certificate `{agent pubkey, tag, scope, expiry, serial}` signed under the tag (per PRD FR5/Q4/M4). Each agent holds its own keypair and signs requests per RFC 9421. Revocation is per-serial; banning the tag kills all agents operating under it.

**Hardware search results (Netherlands, 2026-07-30):**
- **bol.com** — new ACS ACR122U, ~€36.99, next-day delivery, listing notes "werkt voor DigiD" (a good compatibility signal). **Recommended.** https://www.bol.com/nl/nl/p/nfc-rfid-reader-writer-acr122u-wit-werkt-voor-digid/9200000072575675/
- **Marktplaats** — ACS ACR1581U-C1 used, €35, Berlicum, listed May '26 (may be stale); newer model than the ACR122U and supports Type A+B. Also Gemalto ProX-SU, €20, Eindhoven, but Type-B support unconfirmed — a gamble. The one genuine ACR122U listing found there had already sold.
- **123NFC.nl** — ACR122U at €31.47 but currently out of stock.
- **Vinted** — blocks automated browsing; nothing found.
- **Caution:** the ACR122U is EOL and widely counterfeited (especially on AliExpress) — buy from a genuine/reputable source such as bol.

## Next steps

1. ~~Owner orders a reader — bol.com ACR122U (~€37) recommended, or message the Berlicum Marktplaats seller about the ACR1581U-C1 as an alternative.~~ **OBSOLETE** — ACR122U rejected, bol listing dead, owner buying an Android phone instead. See `m0-hardware-android-first.md`.
2. In parallel, the agent can prep the M0 spike harness — pcscd/Java/JMRTD environment setup plus a masterlist loader with a load-count assertion. **Offered, not yet requested** — wait for owner go-ahead.
3. Commit the pending PRD v1.1 + CHANGELOG edits once the owner asks (commit only on request, per standing policy).
4. When the reader arrives: run M0 (two scans → identical tag, timings measured, masterlist load-count asserted vs. file count), write `docs/02-evidence/M0-EVIDENCE.md`, throw away the POC code, then resolve D9 using that evidence.
5. Q6 (Mac-less iOS build path) is deliberately deferred to M2 — do not revisit early.

## Gotchas

- **PRD and CHANGELOG edits are uncommitted in the working tree** — don't lose them; don't let a later session assume v1.1 is already committed.
- `.claude/` directory is untracked in the repo — consider adding a `.gitignore` entry before anything here is pushed.
- The desktop M0 path **cannot** produce Apple App Attest fixtures — the M1 fixture plan has been adjusted to use published Apple sample fixtures as a stand-in, with real fixtures re-verified at M2.
- Cheap MIFARE-only USB readers cannot read Type-B passports — always verify ISO 14443 **Type A and Type B** support before buying a reader.
- Desktop path has no camera/OCR — the MRZ must be typed manually to derive BAC/PACE keys during M0.
- npm placeholder package is still mislicensed as MIT (carried over from founding session) — gated by NO-GO #8, not urgent, but still open.
