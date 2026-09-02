# zkagent — M0 Hardware Decision & Android-First Session Stash

**Timestamp:** 2026-07-31
**Supersedes hardware conclusions in:** `m0-deplatform-hardware.md` (2026-07-30)

## Session summary

Continued from `m0-deplatform-hardware.md`. Two things changed materially. First, the
prior session's hardware recommendation (ACR122U USB reader from bol.com) was
**falsified** — the ACR122U cannot properly read ICAO e-documents, and the specific bol
listing is gone. Second, the owner decided to **go Android-first and buy a phone rather
than a reader**, resolving the D2 platform question early on cost grounds rather than
waiting for M2 evidence. Most of the session was spent establishing the real device
requirement set (which is stricter and more brand-specific than "NFC + recent Android")
and then hunting the NL used market against it. No code was written. No PRD or CHANGELOG
edits were made this session — the v1.1 edits from the prior session are still uncommitted.

## Key decisions

- **Android-first confirmed.** The owner will build and ship Android before iOS, and buy
  the Apple Developer Program membership only when user demand justifies it. Rationale:
  Google Play is $25 one-time vs Apple's $99/yr; Android builds on the owner's Fedora box
  with no Mac; test builds sideload free with no entitlement gate. This is Q6 option (b)
  from PRD v1.1, taken early. **Not yet written into the PRD** (D2 still says iPhone-first).
- **Buy a phone, not a USB reader.** The reader would only cover M0 and gets thrown away;
  the phone covers M0 through M4. Accepted tradeoff: the phone costs more up front and
  removes the cheap "kill D8 for €35" insurance option.
- **ACR122U rejected** (see Findings). If a reader is ever revisited, the correct part is
  the **ACR1581U-C1** (ACS DualBoost III), not the ACR122U.
- **Target device: Pixel 8a > Pixel 7a > Pixel 6a**, stock ROM only. Any of these works;
  they differ only in how long they keep passing Play Integrity.
- **Budget reality accepted.** The owner's original target (~€50, "not 70") does not exist
  in this market for a working, still-supported device. True floor is **€80–110**.

## Doc/repo state

- `docs/archive/zkagent-prd.md` — still v1.1 from the prior session. **Still uncommitted.**
  Does **not** yet reflect the Android-first decision (D2, Q5, Q6 all still open/stale).
- `CHANGELOG.md` — still has the prior session's `[Unreleased]` entry. **Still uncommitted.**
- `.gitignore` — **created this session** (was absent). Ignores `.claude/settings.local.json`,
  `.barebrowse/`, `.litectx/`, plus pre-emptive entries for real passport material
  (`fixtures/real/`, `*.mrz`, `*.dg1`, `*.sod`). Deliberately does **not** ignore
  `.claude/stash/` or `.claude/remember/` — `AGENT_RULES.md` is cited normatively by the
  PRD's NO-GO items and belongs in version control.
- Commit policy unchanged: commit only when the owner asks.

## Findings index

**Reader hardware — prior recommendation overturned:**
- **The ACR122U is not suitable for ePassports.** It cannot handle APDU commands longer
  than **261 bytes**, and it does not propagate time-extension requests to the host, so
  APDUs needing more processing time fail. Documented explicitly as "not suitable for
  testing ICAO e-documents." Firmware below 2.06 has additional ISO 14443 problems.
- The prior session's evidence for the ACR122U was a bol listing whose selling point was
  *"werkt voor DigiD"* — that is a claim about Dutch **ID cards**, not about ePassport
  secure messaging. It was not evidence for this use case.
- That bol listing (~€36.99) is **gone**. The only ACR122U on bol now is €96.95 from a
  third-party seller with 1–2 week delivery.
- **Correct part if a reader is ever needed: ACR1581U-C1 (ACS DualBoost III).** Supported
  by the `acsccid` PC/SC driver on Linux, which handles **extended APDUs up to 64kB** —
  exactly the constraint the ACR122U fails. One was listed new-in-box at €35 in Berlicum
  (pickup only, seller had 3 months tenure and one 2.0★ review).

**Device requirement set (the real filter, tiered):**

*M0 only — nearly any NFC Android qualifies:*
1. NFC with IsoDep, ISO 14443 **Type A and B** (Dutch passports are Type B)
2. Android 8+ for modern JMRTD/tooling
3. Extended-length APDU is nice-to-have but largely moot here — the tag derives from
   DG1 (MRZ) + SOD, both small. DG2 (face image) is the file that trips the 261-byte
   limit, and zkagent does not need it.

*M2 onward — this is where the pool collapses:*
4. **StrongBox-backed Keystore** (discrete secure element) for PRD line 24's enclave property
5. **Play Integrity `MEETS_STRONG_INTEGRITY`** — requires a security patch from within the
   last **12 months** (rule introduced May 2025, Android 13+)
6. Biometric sensor for the gate
7. **Stock ROM** — custom ROMs fail Play Integrity outright

**StrongBox is brand-specific silicon** (the reason the search looks Pixel-shaped):
- Google Pixel 3 and later — Titan M / M2
- Samsung Galaxy S21 and later — Knox Vault
- Everyone else (Xiaomi, Motorola, Sony, OnePlus, Nothing, Fairphone, Samsung A-series)
  generally has TEE-backed Keystore but **no** discrete SE →
  `setIsStrongBoxBacked(true)` throws `StrongBoxUnavailableException`.

**Support runway — the deciding factor, since strong integrity dies 12 months after EOL:**

| Device | Security updates until | Strong integrity until |
|---|---|---|
| Pixel 3a / 4a | ended Aug 2023 | already failing |
| Pixel 6 | Oct 2026 | ~Oct 2027 |
| Pixel 6a | Jul 2027 | ~Jul 2028 |
| Pixel 7 | Oct 2027 | ~Oct 2028 |
| Pixel 7a | May 2028 | ~May 2029 |
| Pixel 8 | Oct 2030 | ~Oct 2031 |
| Pixel 8a | May 2031 | ~May 2032 |
| Galaxy S21 | ended Nov 2025 | ~Nov 2026 |
| Galaxy S22 | ~early 2027 (final year, quarterly) | ~2028 |
| Galaxy S23 | ~2028 | ~2029 |

Consequence: **Pixel 4a and 3a are dead for this project** despite having Titan M — they
cannot pass strong integrity. Samsung is a legitimate alternative but the current market
doesn't favour it: S21 is already EOL, and the cheapest S22 (~€179 w/ 12mo warranty) has a
*shorter* runway than a Pixel 6a costing half that.

**Release-strictness (answered this session, affects product not just hardware):**
- The Pixel requirement is a **dev-device** requirement, not a user requirement. The shipped
  app runs on essentially any NFC Android.
- Strictness is a policy dial: requiring StrongBox + `MEETS_STRONG_INTEGRITY` would exclude
  most of the Android install base. Since NO-GO #5 caps the product at **captcha-grade**,
  the right default is permissive — require NFC + TEE-backed keystore +
  `MEETS_DEVICE_INTEGRITY`, treat StrongBox and strong integrity as a bonus when present.
- **Unresolved tension worth a PRD question:** FR6 says all clients emit identical-shaped
  payloads and metadata must not fingerprint, flagged "not retrofittable." But exposing a
  device-assurance tier (StrongBox vs TEE) is exactly such metadata and shrinks the
  anonymity set. Either accept a small fingerprinting cost, or stay uniform and enforce one
  global hardware bar. **Cheaper to settle before M2.** Suggested as new **Q7**.

**NL market findings (2026-07-30/31):**
- **Vinted is not a viable source.** Five separate cheap Pixel listings were checked and all
  five were broken-display units (two confirmed by the owner reading the seller
  descriptions: a Pixel 7 whose screen shows no image, and a Pixel 6a with damaged LCD reset
  "via Fastboot alla cieca"). The sub-€80 Vinted Pixel supply is drop-damaged phones being
  flipped. Working units there cost the same as Marktplaats with worse recourse and
  cross-border shipping. Vinted's search API also returns **stale/removed listings**, and
  item pages bot-challenge after a few requests.
- **Marktplaats is the viable market.** Live shortlist at time of writing:
  - Pixel 8a, Amersfoort, **no asking price** — https://www.marktplaats.nl/v/telecommunicatie/mobiele-telefoons-apple-iphone/m2422008936-te-koop-aangeboden-een-google-pixel-8a-128gb-in-top-staat
  - Pixel 8a black, Amersfoort, **no asking price** — https://www.marktplaats.nl/v/telecommunicatie/mobiele-telefoons-overige-merken/m2411412868-google-pixel-8a-zwart-in-nette-staat
  - Pixel 6a 128GB, €110 asking, Akersloot (listed 29 Apr, stale) — https://www.marktplaats.nl/v/telecommunicatie/mobiele-telefoons-overige-merken/m2394070960-google-pixel-6a-128gb
  - Pixel 6a white, €120 asking, Enkhuizen (listed 24 Apr, stale) — https://www.marktplaats.nl/v/telecommunicatie/mobiele-telefoons-overige-merken/m2392348676-google-pixel-6a-wit
  - Pixel 6a ×2, Groningen, open bid — m2419347859 and m2418408981
  - Pixel 7a €175 **buy-now with "Direct kopen"**, Tilburg, stock ROM — https://www.marktplaats.nl/v/telecommunicatie/mobiele-telefoons-overige-merken/m2422722183-google-pixel-7a-128gb-pure-android-5g
  - Pixel 8a €199 buy-now, shop w/ 3mo warranty + invoice, Lelystad — https://www.marktplaats.nl/v/telecommunicatie/mobiele-telefoons-samsung/m2412196481-google-pixel-8a-128gb
- On Marktplaats you can message **any** seller with an offer regardless of listing type;
  "bieden" only sets the default buy button.
- Seller screening questions that cover every dealbreaker encountered: does NFC work; is it
  stock Android (no custom ROM); is it factory reset and free of the previous Google account
  (FRP); does the screen show an image **and** respond to touch.
- Cosmetic damage (scratches, dents, cracked glass over a *working* display), weak battery,
  and a broken camera are all **acceptable** for this use case — worth telling sellers, as it
  unlocks discounted units other buyers reject.

## Next steps

1. Owner works the Marktplaats shortlist: open at €90–110 on the two Amersfoort Pixel 8a
   listings, offer €80 on Akersloot and €85 on Enkhuizen, €60–70 on the Groningen pair.
   Fallback after ~a week: buy the €175 Tilburg Pixel 7a outright.
2. Once a device lands: run M0 on it with JMRTD on Android — two scans → identical tag,
   timings measured, masterlist load-count asserted vs file count. Write
   `docs/02-evidence/M0-EVIDENCE.md`, throw the POC away, then resolve D9 on that evidence.
3. **PRD edits still owed** (none made this session): flip D2 to Android-first, resolve Q5
   and Q6 accordingly, and add Q7 (device-assurance tier vs FR6 uniformity, target M2).
4. Commit the pending PRD v1.1 + CHANGELOG + new `.gitignore` when the owner asks.
5. M0 test vectors: build the committed ones from ICAO **sample/synthetic** documents. Real
   passport vectors stay local — DG1 bytes are the owner's own MRZ.

## Gotchas

- **Do not buy an ACR122U.** The prior stash recommends it; that recommendation is wrong.
  See Findings. This file supersedes it.
- **Do not buy a GrapheneOS / custom-ROM / "SafePixel" / Kali NetHunter phone.** Six such
  listings were seen across both sites at €131–329. They fail Play Integrity permanently and
  are useless from M1 onward.
- **Do not buy a Pixel 4a or 3a** despite their low price — EOL since Aug 2023, cannot pass
  strong integrity.
- **FRP-locked phones ("bloqueado", "cuenta Google") are bricks** — they cannot be set up at all.
- A phone with a **dead display but working touch is still unusable**: it's freshly reset, so
  USB debugging is off, and it can't be enabled without seeing the setup wizard. `scrcpy`
  can't help because ADB isn't available yet. This killed both Vinted candidates.
- PRD and CHANGELOG edits from the **prior** session remain uncommitted — don't assume v1.1
  is committed, and don't lose them.
- The desktop-reader path can never produce Apple App Attest fixtures; with Android-first,
  M1 uses published Apple sample fixtures and real fixtures are deferred (see PRD line 82).
- Environment: `grep` on this host is **ugrep 7.5.0**, which rejects bounded-repetition
  regexes GNU grep accepts (fails slowly, looks like a hang). Parse structured data with
  `python3` instead — scraped pages usually embed `__NEXT_DATA__` JSON.
- npm placeholder package is still mislicensed as MIT (carried from the founding session) —
  gated by NO-GO #8, not urgent, still open.
