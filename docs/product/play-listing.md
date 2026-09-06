---
type: reference
title: zkagent Scanner — Play Console Crib Sheet
status: draft
sources: [docs/wiki/milestones.md §6.6, docs/wiki/decisions.md D1/D17/D38/D74/D76/D78/D79, docs/product/customer-guide.md, docs/product/privacy-policy.md, apps/scanner/app/src/main/AndroidManifest.xml, apps/scanner/app/build.gradle.kts, apps/scanner/README.md]
---

# zkagent Scanner — Play Console Crib Sheet

> **REFERENCE ONLY (D83, 2026-09-06).** The owner's Play developer account was closed for
> inactivity and the owner declined to open a new one — no Play listing is happening for the
> owner's showcase build. Distribution is GitHub Releases + sideload instead (see
> `docs/wiki/decisions.md` D83, `docs/wiki/milestones.md` §6.6/§6.8). This document is kept for
> any operator who chooses to list their own build on a store. Its "VERIFY" items were never
> checked against a live console — nothing in this doc was validated by a real upload.

**Internal document — for the owner filling out the Google Play Console.**
Not user-facing, never linked from the store listing itself. Prerequisite
work for PRD §6.6 item 1 (closed-testing upload). Google's own console UI
and policies change over time — anywhere this doc says **VERIFY**, check the
live console/policy page at upload time rather than trusting this doc.

## (a) App details

- **App name suggestion:** "zkagent scanner (showcase)" — the parenthetical
  signals this is a demo/showcase build, not a production identity app.
  Owner's call on final wording; avoid anything implying an official
  government or bank affiliation.
- **Package name:** `com.zkagent.scanner` (fixed — `apps/scanner/app/build.gradle.kts`
  `applicationId`; every future update to this listing must ship the same
  package name and be signed by the same upload key, §6.6 item 7).
- **Category:** Tools, or Utilities — **VERIFY** which category Play offers
  at submission time; avoid "Finance" or "Identity" categories which invite
  the stricter identity-app policy review path (see (e) below) — arguably
  unavoidable here, but don't self-select into it unnecessarily via category
  choice.
- **Track:** Closed testing ONLY (PRD §6.6 item 1, D75). Do not create a
  production release from this app. Internal testing track is a plausible
  alternative to closed testing for the very first upload if the console
  offers it — **VERIFY** which one actually unlocks production-track
  eligibility fastest, since item 4's tester-count-over-time gate is
  Google's current policy and must be read live.

## (b) Data safety form

Google's framing: "collected" = transmitted off the device to anyone,
including a third party the user chose; "shared" = transmitted to a party
other than the app's own developer for that party's own purposes.

**What actually happens, worked through carefully:**

- The app computes a yes/no age answer (and, tier B only, a per-website
  pseudonymous tag) from data read off the passport/ID chip, and sends that
  answer to the website the user opened the scan from — never to a server
  run by this app's developer (customer-guide.md §1, §12; decisions.md D3,
  D17).
- Under Google's definitions, this transmission likely counts as
  **"collected"** (it leaves the device) even though nothing is retained on
  any server the developer controls. Whether it also counts as **"shared"**
  is a genuine judgment call: the recipient is a third party from the app
  developer's point of view (the website, not us), but the user
  affirmatively directed the transmission to that specific website by
  opening the scan from it (functionally closer to "you told this website a
  fact about yourself" than to the app silently handing data to an ad
  network). Two honest ways to answer, both defensible:
  1. **Recommended: answer "No data collected or shared"** on the strict
     reading that nothing is retained, logged, or usable by the app's
     developer, and that what is sent is a derived boolean/pseudonym never
     traceable back to identifying document fields — not "your data," in
     the sense the form is probing for.
  2. **Alternative, more conservative: answer "Shared" → "Personal info" →
     "Other info"** for the age-boolean/zktag transmission, on the reading
     that any off-device transmission of a value derived from a government
     ID is safest declared even when non-identifying, and note in the
     "why" field that the recipient is user-selected per scan, not a fixed
     third party, and that no raw document field is ever included.
  - **The owner must make the final call here** — this is a policy
    interpretation, not a technical fact this document can settle. Err
    toward the conservative answer (2) if in doubt, since overstating data
    practices to Google carries far lower risk than understating them.
- **Data types, if declaring anything:** "Personal info" → likely closest
  fit is an "Other info" or "App activity" bucket for the derived
  age-boolean/pseudonym — there is no clean fit for "a boolean derived from
  a government ID, never stored" in Google's fixed type list. **VERIFY**
  against the current form's exact type list at submission time.
- **Encryption in transit: Yes.** The release build's network security
  config (`apps/scanner/app/src/main/res/xml/network_security_config.xml`)
  forbids cleartext entirely; only the debug build carries a narrow
  exception for `127.0.0.1`/`localhost`/`10.0.2.2` used during local
  development over `adb reverse` (D76) — the Play-distributed build is the
  release config, HTTPS-only, no exception.
- **Data deletion request mechanism: Not applicable / no server-side data.**
  Nothing is stored server-side by the app's developer; there is nothing to
  request deletion of. State this plainly rather than leaving the question
  unanswered — Google's form generally wants an explicit "not applicable"
  reason, not a blank.
- **Data collected from children:** No — see privacy-policy.md's children
  section; the app is not directed at children.

## (c) Permission declarations / sensitive-permission justification

| Permission | Justification text (draft) |
|---|---|
| NFC | "Required to read the chip embedded in a passport or ID card during a user-initiated scan. Used only while a scan is actively in progress, never in the background." |
| USE_BIOMETRIC | "Used to require the device owner's fingerprint, face, or PIN before the app signs a pseudonymous 'recognise me again' response for a website, protecting a locally-generated cryptographic key. Biometric data itself is handled entirely by Android and never reaches this app." |

INTERNET and ACCESS_NETWORK_STATE are not "sensitive"/dangerous permissions
under Android's model and typically need no separate justification text in
the console, but state their purpose in the data-safety narrative anyway
(sending the scan result to the requesting website; checking connectivity
first) for consistency with the permissions table in privacy-policy.md.

## (d) Content rating questionnaire hints

- The app has no user-generated content, no social features, no violence,
  no mature themes — expect the lowest content-rating tier available.
- Answer "does the app share the user's location" — **No** (no location
  access requested anywhere in the manifest).
- Answer questions about "personal information" collection consistently
  with whichever data-safety answer the owner picked in (b) above — Google
  cross-checks these two sections and a mismatch is a common rejection
  reason. **VERIFY** the exact wording of the content-rating questions at
  submission time, since Google periodically changes this questionnaire's
  structure (IARC-administered).

## (e) Government ID / sensitive-identity-document policy

Google Play has historically maintained a "Personal Loans" / "Financial
Services" policy family and a broader "Restricted Content" policy area that
can extend scrutiny to apps handling government-issued identity documents,
sometimes called out under a "Government ID" or "Sensitive Info" permissions
group depending on the console version at the time. **The exact current
policy name and its requirements MUST be VERIFIED against the live Play
Console / Play policy center at the time of upload — do not rely on a name
or requirement list invented here.** What to check for, generically, when
verifying:
- Whether NFC-based government-ID reading triggers an extra declaration form
  beyond the standard permissions declarations.
- Whether a closed-testing-only, non-production submission is exempt from
  identity-specific review gates that apply to production listings.
- Whether the "no backend, no server-side storage" architecture (this app's
  actual shape) needs to be explicitly stated anywhere in a policy
  declaration form, separate from the data-safety form in (b).

## (f) Store listing text

**MUST NOT** use the words "zero-knowledge" or "ZK" anywhere in listing text
(NO-GO #7, D1) — this app is not zero-knowledge and must never be marketed
as such.

- **Short description (≤ 80 characters), draft:**
  "Proves your age from your passport/ID chip. Showcase, closed testing."
  (68 characters — leaves headroom for owner wordsmithing.)
- **Full description, draft:**

  > zkagent scanner reads the NFC chip already in your passport or ID card
  > and checks the government's own signature on it, then answers one
  > yes/no question a website asks — for example, "are you over 18" —
  > without sending your name, date of birth, document number, or photo to
  > anyone.
  >
  > This is a showcase build for closed testing only, published to
  > demonstrate the open-source zkagent project
  > (https://github.com/hamr0/zkagent). It is not a production identity
  > product. The app talks only to the website you chose to scan for; there
  > is no account, no server run by this project, and nothing is stored
  > about you anywhere except a plain-language scan log kept on your own
  > phone.
  >
  > Optionally, a website can ask to recognise you on a repeat visit — this
  > uses a per-website pseudonymous tag that cannot be linked to any other
  > website, and requires your fingerprint, face, or PIN each time.
  >
  > Open source, Apache-2.0 licensed. See the project repository for full
  > technical details and honest limitations.

  Review this draft for tone before publishing — it must stay honest about
  "showcase, closed testing" per §6.6 item 5's MUST NOT (no production
  claims from this listing).

## (g) Screenshots rule

**Only use the demo/scanner's value-free report view** (the plain-language
scan log described in ReportLog.kt / customer-guide.md §12 — result, sent,
shared, identity lines, never a raw document field) for any screenshot.
**Never** screenshot a screen showing raw MRZ/passport fields, a name, date
of birth, or document number — the app has no such screen by design (no
`ResultActivity`, §6.2 item 5), but be deliberate about this when composing
screenshots of the demo page too, which does render fields in its own
comparison table for testing purposes. This is both a PRD requirement
(§6.6 item 5) and a known finding class: a raw-field screen is unsafe to
screenshot for the same reason it is unsafe to accessibility-snapshot.

## (h) Checklist — record back into the PRD after upload

After the closed-testing upload, record each of the following back into
`docs/wiki/milestones.md` §6.6 and/or the evidence log:

1. **Play App Signing certificate digest** (§6.6 item 2) — read from the
   Play Console's "App signing" page after the first successful upload;
   this digest is DIFFERENT from the local showcase keystore's digest
   (`1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264`,
   §6.6 item 7) and must never be assumed equal to it.
2. **Every review objection Google raises** (§6.6 item 3) — data safety
   form pushback, NFC permission scrutiny, identity/sensitive-data
   questions, target API level requirements, or anything else the review
   team flags — verbatim where possible, feeding into the PRD before M3b.
3. **Tester-count / period policy numbers actually seen in the console**
   (§6.6 item 4) — the current minimum-tester-count-over-a-fixed-period
   requirement for a new personal developer account to reach production
   eligibility, as shown live in the console, not copied from any prior
   assumption in this repo.
4. **Whether review passed at all** (§6.6 item 6, the riskiest assumption)
   — record pass/fail/pending and, if rejected, the exact rejection reason
   Google gives.

## Caution: privacy policy URL timing

The Play Console requires a privacy policy URL before a closed-testing
release can be submitted. The intended URL is
`https://github.com/hamr0/zkagent/blob/main/docs/product/privacy-policy.md`
— **only use this URL once this branch (`docs/play-listing`) has been merged
to `main`**, since GitHub will 404 on a `blob/main/...` link to a file that
only exists on a feature branch.
