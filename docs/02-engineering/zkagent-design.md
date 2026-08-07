# zkagent — Design & Disclosure Model

**Companion to** `docs/01-product/zkagent-prd.md` (PRD v1.4). This document explains *how the thing works and why it is shaped this way*. The PRD decides what gets built and when. **Where the two disagree, the PRD wins** — this document is description, not commitment.

**Status**: describes an intended design. M0 has not been run. Nothing here is measured. Where a claim depends on evidence that does not exist yet, it says so.

**Audience**: someone deciding whether to adopt it, embed it, or regulate it. Semi-technical: you should be able to read this without knowing what an APDU is, and still come away knowing exactly what crosses the wire.

---

## 1. The one idea

Your passport is a sealed envelope that a government already signed.

Every mainstream way of proving something from that envelope works the same way: **open it, photograph the contents, send the photograph to a stranger, and trust them to be careful.** Upload your ID to a website. Show your card to a shop. Even the privacy-preserving designs mostly move the envelope into a wallet app and add an issuer who hands out extracts on request — better, but now there is an issuer, and the issuer has to exist before you can prove anything.

zkagent does something narrower:

> **Open the envelope inside your own phone. Read one field. Answer one question. Close it. Keep nothing.**

The website never receives a document, a number, a name, or a date. It receives an answer, plus evidence that the answer was computed by code that was not tampered with.

We call the shape of that answer a **window**. The operator chooses how wide the window is. The default is the narrowest window that answers anything at all: **one bit**.

Two properties follow, and they are the whole design:

| | |
|---|---|
| **Narrow by default** | The operator asks for exactly one thing and receives exactly that thing. Not a document, not a date of birth, not an age — a single true/false. Widening the window requires the operator to ask, deliberately, in a way the holder can see. |
| **Fresh by default** | Nothing persistent crosses the wire. Two visits by the same person to the same site look like two different people, because there is no field that could tie them together. Persistence is available — but it is a second, explicitly requested mode, not the default. |

Everything below is mechanism for those two lines.

---

## 2. How it actually works

### 2.1 What is in the document

An ICAO 9303 chip document (a biometric passport, and most modern national ID cards) contains:

- **DG1** — the machine-readable zone: surname, given names, nationality, **date of birth**, sex, document number, expiry date.
- **DG2** — the facial image. zkagent never reads it. (This matters for hardware compatibility; see §8.)
- **SOD** — the Document Security Object: a signed structure containing a hash of every data group, signed by a **Document Signer (DS)** certificate, which is in turn signed by the issuing country's **Country Signing Certificate Authority (CSCA)**.

That last chain is the entire trust root. It exists already. No one had to build it, fund it, or agree to it — 190-odd countries did that decades ago for border control, and the public keys are published.

### 2.2 The read

1. The holder photographs the printed MRZ (the two lines of `<<<` at the bottom of the passport page). This is not decoration: the MRZ is the **key** that unlocks the chip (BAC on older documents, PACE on newer ones). A passport in your pocket cannot be read through the fabric by someone walking past — they would need the printed page.
2. The holder taps the document to the phone. The chip and the phone perform the key agreement, then the phone reads **DG1 and the SOD, and nothing else**.

### 2.3 The check

The phone verifies, locally:

- the SOD's signature, against the DS certificate,
- the DS certificate, against the CSCA certificate for the issuing country, from a **public masterlist** (ICAO PKD, or a nationally published list such as BSI's),
- the hash of DG1 against the hash recorded in the SOD.

If all three hold, the phone knows the date of birth it just read is the one the issuing government signed. If any fails, the answer is not "no" — it is **"I could not check"** (§4).

**No network call is involved in this step.** The masterlist is a file the app already has. Refreshing it is the only network traffic the scanner ever makes, and it is the same file for every user.

### 2.4 The crux: the chip cannot keep your secret — the phone does

This is the part most descriptions skip, and it is the reason the rest of the design looks the way it does.

**An ICAO chip has no selective disclosure.** You cannot ask it "is this person over 18." DG1 is a single signed blob; to learn the date of birth you must read the name, nationality, sex, document number and expiry along with it. The SOD signature covers the whole blob, so you cannot strip fields and still verify it.

So the narrow window is **not** enforced by the document. It is enforced by the software on the phone, which reads everything and then chooses to emit one bit.

Which raises the obvious question: *why would anyone believe that?* A modified app could read the same blob and send the whole thing.

That is what the attestation is for. It is not an afterthought or a bonus signal — **it is the thing that makes the window real.**

### 2.5 The attestation

Before releasing the answer, the phone asks its own hardware to sign a statement roughly meaning:

> *This key was generated inside this device's secure hardware. The device is running a verified boot chain. The application asking is package `X`, signed with certificate digest `Y`.*

That last field is the important one. A modified build of the app must be re-signed with a different key, which changes the digest. The operator's verifier accepts only digests on a list it controls (§7). So a tampered client does not produce an unbelievable answer — it produces an answer that is rejected before anyone looks at it.

The honest summary of the trust model: **the government vouches for the data; the hardware vendor vouches for the code that read it.**

### 2.6 The answer

What crosses the wire, in full, in the default mode:

```
{
  claims:      { over_threshold: true },   // one boolean. that is all.
  attestation: <hardware-signed certificate chain>,
  challenge:   <the operator's single-use nonce, signed>
}
```

There is no document number. No date of birth. No age. No name. No nationality. No user identifier. No device identifier. No account.

### 2.7 The verifier

The operator installs one npm package (`chiproof`) and calls it. It is stateless: it holds no database, phones no home, and stores nothing about anyone. It checks four things — the attestation signature, the client's identity against the operator's list, the nonce (single use), and the claim shape — and returns a verdict.

### 2.8 End to end

```
  Operator's site                Holder's phone                 Document
        │                              │                            │
        │──── challenge ──────────────▶│                            │
        │   {mode:"anonymous",         │                            │
        │    threshold:18,             │─── photograph MRZ ────────▶│
        │    nonce:"…"}                │                            │
        │                              │◀── tap: DG1 + SOD ─────────│
        │                              │                            │
        │                              │  verify SOD → DS → CSCA    │
        │                              │  (local masterlist)        │
        │                              │  evaluate: DOB ≤ today-18? │
        │                              │  attest: unmodified app    │
        │                              │  forget everything else    │
        │                              │                            │
        │◀─── {over_threshold:true,    │                            │
        │      attestation, nonce} ────│                            │
        │                              │                            │
   chiproof.verify() → {ok:true, allowed:true}
   operator stores: nothing
```

---

## 3. The two windows

The default window is one bit. There is exactly one other window, and it must be asked for.

### Mode A — anonymous *(default)*

**Emits:** the requested claim, the attestation, the nonce response. Nothing else.

**The operator learns:** that a real, unmodified client read a genuine government document and that the holder satisfies the stated predicate. One bit.

**The operator cannot:** recognise the holder on a second visit, link this visit to any other visit, count distinct users, block an individual, or build a profile. Not because we ask them not to — **because the field does not exist.**

**Nothing is cached that could become an identity.** Mode A derives no secret. There is no key on the phone that represents "you."

This is the age-verification mode, and it is the one the product leads with.

### Mode B — pseudonymous *(opt-in)*

**Additionally emits:** a `zktag` — a domain-scoped pseudonym.

```
secret = KDF(stable data from the chip)          // stays in secure hardware, expires
zktag  = HMAC(secret, the verified site domain)  // domain computed on-device, never
                                                 // accepted from the server
```

Because the domain is mixed in, and because the phone determines the domain itself rather than trusting the site to declare it:

- **same person + same site → same zktag, always.** The site can recognise a returning holder.
- **same person + different site → unrelated zktags.** Two sites comparing notes learn nothing. There is no shared identifier to join on.

**What this buys:** deduplication ("one account per human"), blocking that survives a reset (new email, new IP, new device — same human, same zktag, still banned), and the ability to hang agent delegation off a human root.

**What it costs:** the site can link your visits to *it*. That is not a bug — it is the entire feature. It is also why it is not the default.

### Side by side

| | Mode A — anonymous | Mode B — pseudonymous |
|---|---|---|
| Crosses the wire | claim + attestation + nonce | …plus `zktag` |
| Same person, two visits, same site | indistinguishable | recognisably the same |
| Same person, two different sites | indistinguishable | unrelated pseudonyms |
| Site can block an individual | no | yes, permanently |
| Site can count unique humans | no | yes (bounded — see §9) |
| Secret stored on the phone | none | yes, in secure hardware, with an expiry |
| Holding several documents | costs nothing | means several identities (§8) |
| Typical use | age gate, one-off eligibility | account uniqueness, ban enforcement, AI-agent accountability |

### The rules around the switch

1. **Mode B must be requested explicitly** in the challenge. It is never inferred, never defaulted to, never "upgraded" because the site would find it convenient.
2. **The holder is told which is being asked.** The app shows the difference in plain words — *"this site will only learn that you are over 18"* versus *"this site will be able to recognise you again."*
3. **A mode-A response looks identical whether or not that phone has ever done a mode-B response.** Having a pseudonym somewhere must not be detectable anywhere else.
4. **No stable identifier may appear in mode A** — not a hashed anything, not a device id, not a "rate-limit key", not "just for fraud detection." This is a permanent prohibition in the PRD (NO-GO #11) because the pressure to add one will arrive, and it will sound reasonable every time.

---

## 4. When the check fails

There are three outcomes, not two, and conflating two of them is the classic failure of this kind of system:

| Outcome | Meaning |
|---|---|
| `ok:true, allowed:true` | checked, and the answer is yes |
| `ok:true, allowed:false` | checked, and the answer is no |
| `ok:false, allowed:null` | **could not check** — never rendered as "no" |

A verifier that cannot reach its masterlist, or half-loaded it, or cannot reach the attestation root, must say *"I don't know"* — never *"no."* A broken verifier that answers "no" turns away every legitimate person while looking perfectly healthy on a dashboard. So `ok:false` always carries `allowed:null`, and the operator decides what to do about it.

This invariant is inherited verbatim from the 8een project, where the recurring bug it prevents was found more than seven times.

---

## 5. Is this zero-knowledge? No — and here is the honest version

A zero-knowledge proof lets the verifier check the mathematics itself. The proof carries the guarantee.

| | What the verifier actually does |
|---|---|
| **Zero-knowledge** | "Here is a proof. Check it yourself." |
| **zkagent** | "Here is an answer. The hardware vendor attests that unmodified code computed it." |

The verifier never checks a proof. It checks that trustworthy code ran, then believes the number that code produced. **Break the attestation and the claim collapses.** With a real ZK proof, breaking the attestation buys you nothing, because the mathematics still has to hold.

**What is genuinely comparable:** the privacy outcome in mode A. One bit crosses the wire; nothing links two presentations. A ZK age proof achieves the same visible result.

**What is not comparable:** the trust model. ZK removes the need to trust the prover. zkagent relocates that trust to a hardware vendor. That is a weaker guarantee — and a shippable one, today, with no issuer, no wallet, and no ecosystem that has to exist first.

**Why the project is still called zkagent:** "zk" names the direction of travel, and a ZK tier is a named future upgrade. The standing rule in the PRD is that **the name may be aspirational; the claims may not.** Nothing shipped, published, written or said may describe v1 as a zero-knowledge proof. If you want a phrase for what this is, the accurate one is **attested windowed disclosure**.

**One more correction worth stating, because it is widely assumed backwards:** uniqueness is *not* a ZK property. ZK gives selective disclosure with proof. Uniqueness comes from the credential being scarce — one passport per person. The two pull against each other: a ZK age proof is unlinkable by construction, which makes it useless for "have I seen this person before." That is precisely why mode B exists as a separate mode, and why it cannot be built out of a ZK proof.

---

## 6. Legal posture: the spirit, not the certificate

**What we are doing, stated plainly:** demonstrating that the privacy properties the EU age-verification rules are reaching for can be achieved with dramatically less machinery than the official route requires. We are not asserting compliance, not seeking certification, and not arguing with the regulation.

The EU's Age Verification Blueprint is explicit that linkability is the enemy, not a requirement. Its own normative text:

- *"An Age Verification App SHALL use a Proof of Age attestation only once and then remove it from the batch of the issued attestations."* (§4.2)
- *"An Attestation Provider SHALL support batch issuance of Proof of Age attestations."* (§4.3)
- *"An Attestation Provider SHALL set the timestamp included in the `ValidityInfo` structure with a precision that limits the linkability information."* (§4.3)
- *"Domain-specific identifiers, or pseudonyms, are used to enable users to avoid relying on the same unique identifier when interacting with online services."* (§2.4)
- *"An Age Verification App SHALL rely on the device's native cryptographic hardware capabilities, such as the Secure Enclave on iOS, or the Trusted Execution Environment (TEE) and Strongbox on Android."* (§4.2)

Read those in order and the architecture is visible: they want each presentation to be unlinkable from the last, so they issue disposable one-shot attestations in batches of thirty, and they blur the clock fields because a timestamp is a correlator.

**All of that machinery exists to work around one constraint: their wallet must round-trip to an attestation provider, and it cannot do zero-knowledge.** So freshness has to be manufactured in advance, thirty at a time, and refilled.

zkagent has no issuer in the path. The phone re-derives the answer from the document each time. **Freshness is not manufactured — it is the absence of anything to persist.**

| Property the EU design pursues | Their mechanism | zkagent mode A |
|---|---|---|
| Presentation cannot be linked to the last | one-time-use attestations, discarded after use | nothing persistent is ever emitted |
| Supply of unlinkable proofs | batch issuance, ~30 at a time, refilled | not needed; derived on demand |
| Timestamps must not correlate | coarsen `ValidityInfo` precision | same rule applies to every field we emit (see the unlinkability budget, §9) |
| Site learns only eligibility | one bit, not the birth date | one bit, not the birth date |
| No persistent identifier across sites | domain-specific pseudonyms | no identifier at all in mode A; domain-scoped in mode B |
| Issuer must not learn where you went | attestation provider is offline at presentation | there is no issuer to learn anything |
| Keys must live in real hardware | Secure Enclave / TEE / StrongBox | same, and additionally attested |

**What we explicitly do not claim:**

- That meeting these properties confers any legal standing anywhere. A rule that requires a *certified provider* makes technical equivalence irrelevant, and no amount of good cryptography changes that.
- That zkagent is part of, endorsed by, or interoperable with the EU Digital Identity Wallet ecosystem. It is not. It is a demonstration of an alternative route to the same privacy properties.
- Any coverage or compliance figure for any jurisdiction. None has been checked.

The PRD keeps this open as a question that must be answered against a *named* jurisdiction before age verification becomes a sales pitch rather than a demonstration.

---

## 7. What the operator controls

Everything policy-shaped is the operator's. Everything security-shaped is not.

```js
const verifier = new Verifier({

  // ── the window ───────────────────────────────────────────────
  mode: 'anonymous',              // 'anonymous' (default) | 'pseudonymous'
                                  // pseudonymous must be chosen; it is never inferred
  threshold: 18,                  // the single age predicate. one bit comes back.

  // ── which documents count ────────────────────────────────────
  acceptedDocuments: ['passport', 'eid'],   // default: everything readable
                                            // narrow to ['passport'] if you need
                                            // uniqueness (see §8)

  // ── which clients you believe ────────────────────────────────
  trustedClients: [
    { name: 'zkagent official', package: 'org.zkagent.app',
      certDigest: 'sha256:AA:BB:…', specVersion: 'zkagent-derivation/1' },
    { name: 'Politie NL ID',    package: 'nl.politie.id',
      certDigest: 'sha256:CC:DD:…', specVersion: 'zkagent-derivation/1' },
  ],

  // ── the government trust root ────────────────────────────────
  masterlist: './csca-masterlist.pem',

  // ── freshness (pseudonymous mode) ────────────────────────────
  maxScanAgeDays: 30,             // answered as one bit, never as a number of days

  // ── your storage, not ours (pseudonymous mode) ───────────────
  nonceStore:     redis,
  blocklistStore: myStore,
})
```

### Why the trust list belongs to the operator

Anyone may embed the core in their own application — including a government. The Dutch police could ship an identity app built on it. That is a feature: it turns a two-sided market ("nobody will check a signal nobody carries") into a one-sided one, because the adopter brings their own users.

But if anyone can build a client, someone will build a malicious one. Open source does not make an application trustworthy — **the signing key does.**

So the operator holds the list. A police force publishes three facts, once, on its own website:

```
package:       nl.politie.id
cert digest:   sha256:CC:DD:EE:…
spec version:  zkagent-derivation/1
```

A webshop adds that entry to `trustedClients`. Done. No API key, no contract, no registration, no relationship with us — and **no registry for us to run, be pressured to change, or be blamed for.** We publish no list.

### Why the derivation is a published specification

`zktag = HMAC(KDF(chip data), domain)` mentions no application. Same document, same site → same pseudonym, whichever conformant client computed it.

This is what makes a borrowable core survivable. An operator can trust the official app *and* the police app, and still ban one human exactly once. Were the derivation an implementation detail, two clients would compute different pseudonyms for the same person, the identity space would fork, and blocking would break silently — the worst possible failure mode for a safety feature. So the derivation is a versioned public spec, and the version travels in every payload and every trust-list entry.

### The consequence nobody should discover late

Because the attestation names the client, **an operator can tell which app you used.** That is not a leak — it is the mechanism the trust list runs on. But it does mean your anonymity set is *"users of this app, in this mode,"* not *"all zkagent users."* An app with ten thousand users offers a crowd three orders of magnitude smaller than one with ten million.

Operators that trust several clients enlarge their users' crowds. Operators that trust exactly one shrink them. This is written down here rather than discovered later, and the PRD requires it to be **measured, not assumed**.

### What operators deliberately cannot configure

- **Turning off the government-signature check.** There is no "trust this document anyway."
- **Receiving the date of birth, the document number, or the name.** No configuration exposes these. They are not withheld by policy; they are never sent.
- **Making mode B implicit.** Pseudonymity is always an explicit request, always visible to the holder.
- **Widening a single presentation to several predicates.** One question per presentation.
- **Asking for a "just this once" identifier in mode A.** See §3, rule 4.

---

## 8. Documents: passports first, ICAO next

**Why passports first.** They are the most standardised ICAO 9303 documents in existence, the same read path works across essentially every issuing country, and the trust chain is genuinely global. If the core cannot read a passport reliably, nothing above it matters — which is why the very first milestone is a throwaway spike that does nothing but read one real passport twice.

**What comes next.** "ICAO 9303 chip document" is the actual boundary, and it includes most modern EU national identity cards and some residence permits — anything carrying an SOD chain verifiable against a public masterlist. Many national ID cards are readable by the identical code path. Some are not: certain national eID functions use a different protocol stack entirely and would be separate work. This is a per-country empirical question, and the PRD forbids stating coverage numbers until real documents have been read.

**What is excluded, permanently, not "later":** US driving licence PDF417 barcodes and their equivalents. They carry text with **no verifiable signature** — there is nothing for the check in §2.3 to check, and anyone with a printer can produce one. Likewise photographs of documents, photocopies, and OCR of any document. Signed mobile credentials (US mDL) may qualify eventually, but they are a different read path, not a widening of this one.

**How adding documents interacts with the two modes** — this is the elegant part, and it is why the default is generous:

- **In mode A, holding several documents costs nothing.** No identifier is emitted, so a person with a passport and an ID card is not two identities — they are one person with two ways to answer the same question. Accept everything readable; reach is free.
- **In mode B, each accepted document type is a way to mint another pseudonym.** A holder of two documents can produce two unrelated identities at the same site. So uniqueness is always *"at most k,"* where k is roughly the number of documents held (1–3), and an operator who genuinely needs k≈1 narrows `acceptedDocuments` to passports and knowingly trades reach for it.

**Unifying a person's documents into one identity is an explicit non-goal.** It is technically possible in countries that print a national personal number in more than one document — and it is exactly the kind of cross-document identifier this project exists to avoid creating. Two documents, two identities, and that is fine.

---

## 9. What this design does not protect against

Stated because a trust product that hides its limits is worse than one that has none.

- **A borrowed or stolen document.** Anyone holding your passport and your phone can present as you. Mitigations: the biometric gate on the phone, and an expiry on the derived secret so a single borrowed scan buys weeks rather than forever. It is not eliminated.
- **A coerced scan.** Nothing technical prevents someone standing over you.
- **A chip clone (mode B).** Verifying the SOD proves the *data* is genuine; on its own it does not prove the *chip* is. A copied data set could, in principle, mint the same pseudonym as the original document. Defending this needs the chip's own challenge-response (Active or Chip Authentication) as part of the read. **This is an open engineering item, and it is load-bearing for the uniqueness claim specifically** — mode A is unaffected, since there is nothing to impersonate.
- **A break in hardware attestation.** This is the load-bearing assumption of the whole design (§2.5). Attestation keys have been extracted from real devices before. The honest framing is that this is **captcha-grade** assurance: enough to make abuse expensive at scale, nowhere near enough for a bank.
- **The attestation leaking an identifier of its own.** Mode A's promise covers the *whole payload*, and the attestation is part of it. A reused key, a device-unique certificate intermediate, an OS patch-level string or a precise timestamp would each reintroduce linkability through the back door while we claimed it was gone. Until this is measured — with a deliberately planted stable field proving the test can fail — **mode A is a design intent, not a demonstrated property.**
- **An operator correlating by other means.** If you log in first and then verify, the site obviously knows both. zkagent removes an identifier from the protocol; it does not remove the ones the site already had.
- **A small anonymity set.** See §7. A niche client build is a small crowd.
- **Legal sufficiency anywhere.** See §6.

---

## 10. Where the open questions live

This document describes the design. The unresolved parts live in the PRD, and the important ones are:

| Topic | PRD reference |
|---|---|
| Which attestation root — and whether it works without Google services | Q14 |
| Does the attestation payload defeat mode A's unlinkability | Q15, FR9, milestone M1b |
| Which chip field survives document renewal | D9 |
| Chip cloning vs the uniqueness claim (§9) | open — to be recorded |
| Whether one bit can still be probed repeatedly | Q11 |
| Which ICAO documents actually read, per country | Q12 |
| Legal sufficiency in a named jurisdiction | Q17 |

**And the one that outranks all of them:** none of this has been run. The first milestone reads one real passport, twice, on one real phone, and writes down what actually happened. Everything above is a hypothesis until it does.
