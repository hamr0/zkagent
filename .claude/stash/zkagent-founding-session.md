# zkagent — Founding Session Stash

**Timestamp:** 2026-07-26

## Session summary

Started as an open-ended strategy discussion — "what's the next unmet need after social media dies" — which converged on a four-scarcities thesis: verification, accountability, collective agency/obligation, and loyalty. From there the discussion moved into an agentic-web gap analysis: agent traffic now exceeds human traffic, RFC 9421 (HTTP message signatures) is emerging as a convergence point, and there is a fork forming between vendor-trust models and principal-trust models for agent identity.

That analysis converged on building **zkagent**: human-rooted, privacy-preserving authentication for AI agents. The core mechanic is a single passport NFC scan producing one anonymous, deterministic tag per service — the tag is `HMAC(chip-derived secret, verified domain)`. Agents then act under revocable delegation certificates tied to that tag. Banning the tag bans all bots operating under it, and the ban is unresettable because it is bounded by the number of passports a person holds (k, realistically ~1-3).

The session ended with a concrete v1 scope (captcha-grade, no ZK circuits), a signed set of owner decisions (D1-D8, with D9 deliberately left open pending M0 evidence), a populated public repo, a placeholder npm package, and a clear next blocker before implementation can start.

## Key decisions

- **v1 scope: CAPTCHA-GRADE, no ZK circuits.** Trust root is the government chip signature, verified locally against the public ICAO/BSI masterlist (no CA involved), plus Apple App Attest for device-level integrity.
- **Root-of-identity split:** NFC (passport chip) roots *identity* — the tag must survive a device change. CPU/secure-enclave roots *custody* only, and is never used in tag derivation.
- **Platform constraint accepted:** Web NFC cannot read passports (platform wall on iOS/Android). Solution: a thin native iOS scanner app wrapping the vetted `NFCPassportReader` library (never write custom chip-parsing code) for the scan step; everything else stays web.
- **Verifier SDK:** stateless Node package, distributed via npm.
- **Owner decisions D1-D8 are signed off.** D9 (derivation field: document-number vs personal-number) is explicitly left open, to be decided on M0 evidence rather than argued in the abstract.
- **AGENT_RULES governs the build**: spec sign-off required before build starts, a POC must target the riskiest assumption first, "prove don't assert," and only measured numbers are acceptable (no estimates presented as facts).
- **Riskiest assumption identified:** issuer-free derivation. The PHC literature (arXiv 2408.07892) assumes an issuer in the loop; nobody has published an issuer-free scheme. This is why M0 exists.
- **Invariant inherited verbatim from 8een:** `ok` (did the checker actually run/check) is kept strictly separate from `allowed` (the answer). If `ok:false`, then `allowed` must be `null`, never `false`. A broken verifier must never affirmatively say "no."
- **zk8een is NOT directly reusable** — `longfellow` is mdoc-only, and a passport chip is not an mdoc. What *is* reused from 8een: the `challenge.js` pattern (HMAC self-auth nonce, single-use), the verdict/never-throw classifier shape, adopter-supplied-store statelessness, silent-partial-load paranoia (the masterlist PEM failure mode — 19 entries in the file but only 17 loaded, silently), and general test discipline.

## Repo/artifact state

**Repo:** `github.com/hamr0/zkagent` — public, Apache-2.0.

**Branch protection:** `main` protected, matching the `bareagent` policy — 1-approval PRs, dismiss stale reviews, linear history required, conversation resolution required, no force-push. Admin-merge allowed only on owner say-so.

**Files in place:**
- `README` — honest-limits-first framing, marked `[WIP]`.
- `CHANGELOG` — keep-a-changelog format; `0.0.0` entry serves as name-reservation marker.
- `LICENSE` — Apache-2.0.
- `docs/01-product/zkagent-prd.md` — PRD v1.0. Contains a NO-GO list (#1-10), including NO-GO #10, the scope gate: "no feature enters a milestone unless it is in the PRD first." Defines milestones M0-M5, each with its own evidence checkpoint.
- `docs/context/future-digital.md` — "the collector" doc: findings F1-F9, parked ideas P1-P6, gaps G1-G4. Merged via PR #1; source branch deleted after merge.

**Commits:** `35586b2` (initial), `58e7480` (context doc), `d3152bf` (merge).

**npm:** `zkagent@0.0.0` placeholder published under the `hamr0` npm account. Publishing requires per-write 2FA browser approval from the owner's own terminal (an agent shell cannot complete OTP). Note: the placeholder `package.json` currently says MIT — this must be corrected to Apache-2.0 before the next real publish. NO-GO #8 in the PRD gates real publishing anyway, so this isn't urgent but must not be forgotten.

**Profile README:** `github.com/hamr0` profile updated with a zkagent entry in the "Privacy primitives" section, placed after 8een, marked `[WIP]`. Commit `d6abd0b`.

## Findings index

Findings, parked ideas, and gaps all live in `docs/context/future-digital.md` (merged, PR #1).

**Parked ideas (all depend on the tag existing):**
- Vouch — human-countersigned AI output; framed as an EU AI Act wedge.
- Signed Clerk — ROSCA-style rotating savings/credit in group chats, multisig-based; carries e-money regulatory kill-risk.
- Fiduciary agent.
- Signed-claims commons.
- Obligation products.
- Under-18-only spaces.

**Gaps identified:**
- Passport-less humans have k=0 — no coverage story for people without a passport.
- Blocklist governance is undefined — who decides bans, on what process.
- Business model is undefined.
- IETF principal-proof field timing is uncertain — unclear when/if a standard field for this lands.

## Next steps

1. **Blocker:** owner must confirm their Apple developer account has the NFC tag-reading entitlement (this is PRD open question Q2). Without it, the scanner app cannot be built at all.
2. Owner's available hardware is iPhone only — no Android device. Android emulator is a documented dead end on this host (not a viable path for M0).
3. Once entitlement is confirmed: spike **M0** — a throwaway iPhone proof-of-concept. Read the owner's real passport, verify the SOD against the public masterlist, confirm the same tag is produced across two separate scans, capture measured timings, and write up an evidence doc. Tooling: Xcode + NFCPassportReader.
4. Milestone sequence after M0: **M1** verifier SDK core → **M2** scanner app rewrite (production-quality, post-POC) → **M3** captcha-replacement demo (the demo site is its own adopter with its own store — the SDK itself still stores nothing) → **M4** agent layer (delegation certs, RFC 9421 middleware) → **M5** blocklist/appeal process.
5. D9 (doc-number vs personal-number as the derivation field) should be resolved using evidence gathered during M0, not decided speculatively beforehand.

## Gotchas

- Web NFC cannot read passport chips — this is a hard platform wall on both iOS and Android, not a bug to work around. The native scanner app is mandatory, not a temporary measure.
- Never write custom passport-chip parsing code — always wrap the vetted `NFCPassportReader` library. This mirrors the "never roll your own crypto/parsing" lesson from prior projects.
- CPU/secure-enclave material must never leak into tag derivation — it roots custody, not identity. Mixing the two would break the "tag survives device change" property.
- zk8een's `longfellow` component is mdoc-only and does NOT apply here — passport chips are not mdocs. Do not assume 8een's ZK stack ports over; only specific patterns (challenge.js, verdict shape, statelessness, partial-load paranoia, test discipline) transfer, not the crypto machinery itself.
- The masterlist silent-partial-load failure mode (19 entries in file, only 17 loaded, no error raised) is a named, real failure class from 8een — the same class of bug must be guarded against here with the ICAO/BSI masterlist loading.
- `ok`/`allowed` separation is an invariant, not a style preference: a broken or incomplete verifier must never resolve to an affirmative "no" — `ok:false` forces `allowed:null`.
- npm publish step needs a human present at a real terminal for 2FA — this cannot be scripted or delegated to an agent shell.
- Placeholder npm package currently mislicensed as MIT; must be Apache-2.0 before any real release (though NO-GO #8 already blocks real release for other reasons).
- PRD NO-GO #10 (scope gate) means no new feature — including anything from the parked-ideas list (Vouch, Signed Clerk, etc.) — can be pulled into a milestone without first amending the PRD.
- Riskiest technical assumption (issuer-free derivation) is currently unproven in the literature; treat it as unproven until M0 evidence says otherwise, per AGENT_RULES' "prove don't assert."
