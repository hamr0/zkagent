# future-digital — the thinking that produced zkagent

**Session record, 2026-07-26.** This is the *collector*: findings and agreements from the
strategy session that ended in the zkagent PRD. The PRD (NO-GO #10) is the *filter* —
nothing moves from here into a milestone without a PRD change and owner sign-off.
This file exists so the three-quarters of the map that zkagent doesn't cover is not lost.

---

## 1. The starting question

Social media is dying and AI is replacing it as the primary way people ingest information.
What is the next *unbuilt* need — a place to talk? come together? think? do something together?

## 2. Findings (agreed)

**F1 — Don't build a hangout.** The "place to talk" need already retreated into closed
groups (WhatsApp/Signal/Discord, rising since COVID) and is served there. Solving social
media with more social media is not a solution. Pure-discourse products cap out —
engagement without consequence goes stale.

**F2 — The serf structure.** Digital life mirrors class: pay and you're the customer,
don't pay and you're the product. Free products enslave; paid services are for the wealthy.
This is structural, not accidental.

**F3 — The ad-web dies of demographics, not ethics.** AI agent traffic passed human
traffic (Cloudflare). Agents don't see ads, so attention-monetization loses its audience.
Monetization flips to machine-payable access. The serf divide doesn't vanish — it re-forms
one layer up as *whose agent you can afford* (trusted lab agent vs blocked open-source one).

**F4 — Four new scarcities.** AI made answers, content, and companionship-simulation
abundant. Value migrates to what AI cannot produce:
1. **Verification** — is this real, is this human, did this happen?
2. **Accountability** — who is bound when something goes wrong?
3. **Collective agency / obligation** — groups that can *act*, not just talk; being
   needed by other humans is the last unfakeable good.
4. **Loyalty** — an agent provably serving you, not a platform.

**F5 — Ground truth sits UNDER AI, never inside it.** A signature proves *who said it*,
never that it's true. AI signing its own output = provenance only (useful, insufficient).
AI-checking-AI is laundering. Ground truth = signed human/sensor claims (capture
attestation, claim attestation with stakes) that AI answers can be *cited against*.
Humans and hardware are the sources; AI is strictly a downstream consumer.

**F6 — Accountability without surveillance (the core resolution).** Accountability does
not require knowing who you are; it requires that consequences reach you and you can't
escape them. Mechanisms: persistence (deterministic tag, can't reset), scarcity (one
passport, k-bounded), stakes (bonds), exclusion (unrenewable ban). **No unmasking
capability, anywhere, ever — a capability that exists can be compelled** (owner decision,
final; quorum/escrow deanonymization rejected as re-centralization).

**F7 — Davies, *The Unaccountability Machine*.** Accountability sinks are built *on
purpose*; AI is the perfect sink, and agents run it at machine speed. Design consequences:
attestations must be scoped and falsifiable (what was checked, what wasn't, bound to a
hash) or they become the ritual they replace; the adoption wedge is the *shield* ("never
be the designated scapegoat"), not the shackle. A one-click "human reviewed" checkbox is
a new sink with cryptographic veneer — refused.

**F8 — The agentic-web gap (grounded).** Transport standards converged on RFC 9421
(Web Bot Auth live at Cloudflare, Visa TAP, Google AP2 mandates as W3C VCs). All root
trust in a **vendor** or a **custodial IdP account**. The IETF's own drafts name the hole
(`sub` overloading, delegation-chain splicing, no anchored origin). The personhood-
credentials literature (arXiv 2408.07892; 2501.09674) calls for the missing root but
assumes an issuer nobody stood up. **The blank field = a unique, anonymous, blockable
human principal. Issuer-free is the novel bit: the passport chip is the already-issued
credential.**

**F9 — Fingerprinting's lesson, inverted.** The fingerprinting arms race exists because
the web has no scarce root — identity leaks out of entropy. Supplying the scarce root
means the job flips: emit zero entropy beyond the proof (uniform clients, client-side
scope binding), and borrow only the service-side consistency checks (wire fingerprint
match, churn detection) — never the surveillance.

## 3. What became zkagent

The root of the stack, and deliberately only the root: one passport scan → one anonymous,
unforgeable, blockable tag per service; agents act under revocable delegations from the
tag. Captcha-grade v1 (chip signature + OS attestation, no ZK circuits), stateless,
no CA, nothing stored. See `docs/01-product/zkagent-prd.md` — decisions D1–D8, NO-GO 1–10.

Sequencing agreement: **root first.** Every parked idea below consumes the tag primitive;
none of them can exist safely without it. Build order is dependency order.

## 4. Parked ideas (the map zkagent doesn't cover)

**P1 — Vouch: human-countersigned AI output.** Nobody signs/attests AI output today.
Chain = AI output hash-signed (model, time, input digest) + scoped human countersignature
("I verified citations; I did not verify the chronology") + credential proof without a
credentials database. Wedge: professionals whose license is their stake (lawyers already
sanctioned for AI hallucinations; EU AI Act human-oversight duties). Sequence: courts
require → professionals adopt defensively (shield) → insurers price it → firms standardize.

**P2 — Signed Clerk: money circles for existing group chats.** Group chats talk but can't
act — no treasury, no memory, no binding decisions. ROSCA (tanda/susu/chit) in a group
chat: AI clerk whose every statement is a signed, checkable ledger entry; multisig
custody (clerk proposes, members execute, nobody — including us — holds funds); link-out
UX, not bots; ban/reputation via the tag ledger; nullifier stops double-joining.
**Kill-risk: e-money licensing — needs a regulatory opinion before code.** Was the
session's strongest "unmet need" finding (do-something-together > talk-together).

**P3 — Loyal / fiduciary agent.** The answer to the serf model isn't another free thing.
Loyalty = *incentive* alignment, not opinion alignment (self-trained models = comforting
lie generators). Properties: paid directly, verifiable pipeline (no hidden instructions,
no third-party log flows), data custody yours, revocable/portable. Loyalty is
verification applied to your own agent — F4's #4 collapses into #1.

**P4 — The signed-claims commons** (ground-truth ledger). Reputation-staked claims with
public track records; witness co-signatures ("I was there / received it"); verified
petitions & polls (one-person-one-signature via nullifier — kills astroturf);
proof-carrying whistleblowing ("I am an employee of X" without which one);
human-made attestation for creative work (the "organic" label as slop floods).

**P5 — Obligation products beyond money.** Time/skill banks, care rotas, tool commons.
Design invariants (agreed): small (Dunbar), a memory (ledger, not chat scroll), stakes
(flaking costs), no landlord (the moment a platform holds the pot, it's a bank again).

**P6 — Under-18-only spaces.** Predicate proofs work both ways: keep *adults* out of
children's spaces with no register of children anywhere — the thing current age
verification structurally cannot do, and the most socially urgent branch of the original
question ("age restrictions are late and don't apply to AI"). Cut from zkagent v1
(two-claims-only rule); lives here so it isn't lost.

## 5. Open gaps (missed, acknowledged, unowned)

**G1 — The passport-less.** The most enserfed people (undocumented, stateless, poor)
have k=0 chipped documents. The remedy's own blind spot: zkagent excludes exactly whom
the serf thesis worries about. Named limitation — never market over it; no v1 answer.

**G2 — Blocklist governance.** Mechanics designed (block, appeal, bond); institution not:
who *should* ban, what makes appeals fair, what stops blocklist operators becoming the
new landlords. Mechanism ≠ institution.

**G3 — zkagent's own sustainability.** No business model in the PRD. Free-and-open with
no revenue is how projects die or get absorbed by what they opposed. Open.

**G4 — Standards timing.** The Signature Agent Card / Web Bot Auth registry drafts are
being written now; a `principal-proof` field extension proposal is cheap and gets the
concept into the concrete. Unowned, time-sensitive.

## 6. One-line synthesis

Group chats gave people back their village; nobody has given the village its
institutions — a root of trust, a clerk, a ledger, a treasury — that work without a
landlord. zkagent is the root of trust. The rest of the map stacks on it, in
dependency order, one PRD at a time.
