// SPDX-License-Identifier: Apache-2.0
// 0.3.0 spec-gap closures (M2 spike findings, owner-approved 2026-08-31):
//   Gap 1 — plugs can see (and bind) the presented zktag via ctx.zktag.
//   Gap 2 — evidence.require accepts a per-tier {A?, B?, C?} object.
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes, generateKeyPairSync, sign as edSign } from 'node:crypto';
import { createVerifier, normalizeRequire } from '../../src/index.js';
import {
  sigEd25519Zktag, zktagSigMessage, ZKTAG_SIG_KEY,
} from '../fixtures/sig-ed25519-zktag-plug.js';

const SECRET = randomBytes(32);
const T0 = 1_800_000_000_000;
const SCOPE = 'example.test';
const attester = generateKeyPairSync('ed25519');

function makeVerifier(evidence, overrides = {}) {
  return createVerifier({
    stores: { nonce: new (class { #m = new Map(); async setIfAbsent(k) { if (this.#m.has(k)) return false; this.#m.set(k, 1); return true; } })() },
    challengeSecret: SECRET,
    scopeDomain: SCOPE,
    threshold: 18,
    tiers: { max: 'C' },
    evidence,
    ...overrides,
  });
}

function presentationFor(challenge, extra = {}) {
  const p = {
    spec: 'zkagent/1', tier: challenge.tier,
    claim: { over_threshold: true, threshold: challenge.threshold },
    challenge, evidence: [],
  };
  if (challenge.tier !== 'A') p.zktag = 'zktag-' + randomBytes(8).toString('hex');
  return { ...p, ...extra };
}

/** Evidence signed by the attester over claim+nonce+scope+zktag. */
function zktagEvidence(claim, nonce, zktag, { privateKey = attester.privateKey, keyId = 'att1', scope = SCOPE } = {}) {
  const sig = edSign(null, zktagSigMessage(claim, nonce, scope, zktag), privateKey).toString('base64');
  return { type: 'test-sig-ed25519-zktag', version: 1, data: { key_id: keyId, sig } };
}

function zktagPlug() {
  return sigEd25519Zktag({ keys: [{ key_id: 'att1', pubkey: attester.publicKey }] });
}

function assertInvariant(v) {
  if (v.ok === false) assert.equal(v.allowed, null, 'ok:false must force allowed:null');
  else assert.equal(typeof v.allowed, 'boolean');
}

// ---------------------------------------------------------------------------
// Gap 1: ctx.zktag + binds.zktag.
// ---------------------------------------------------------------------------

test('GAP 1: a zktag-binding Ed25519 plug verifies end-to-end at tier B', async () => {
  const v = makeVerifier({ require: { B: [ZKTAG_SIG_KEY] }, plugs: { [ZKTAG_SIG_KEY]: zktagPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [zktagEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'evidence-verified');
  assert.deepEqual(out.evidence, [ZKTAG_SIG_KEY]);
  assert.equal(out.zktag, p.zktag);
});

test('GAP 1 ATTACK: zktag-swap — valid zktag-bound evidence under a swapped zktag MUST fail (allowed:false)', async () => {
  const v = makeVerifier({ require: { B: [ZKTAG_SIG_KEY] }, plugs: { [ZKTAG_SIG_KEY]: zktagPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  const victimZktag = 'zktag-victim-' + randomBytes(8).toString('hex');
  // Evidence legitimately signed for the victim's zktag, presented under an
  // attacker's zktag on the same live challenge — the exact replay Gap 1 kills.
  p.evidence = [zktagEvidence(p.claim, c.nonce, victimZktag)];
  assert.notEqual(p.zktag, victimZktag);
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true, 'swap is a checked answer, not a could-not-check');
  assert.equal(out.allowed, false, 'zktag-swapped evidence must be a real no');
  assert.equal(out.reason, 'sig_invalid');
});

test('GAP 1: a zktag-binding plug on a tier-A challenge is the could-not-check path (ok:false, allowed:null)', async () => {
  // Tier ceiling B, accepted at tier A: nothing forbids the item's presence
  // by linkability=none-style rules alone, but no zktag exists to bind.
  const nonePlug = {
    ...zktagPlug(), linkability: 'none',
  };
  const v = makeVerifier({ accept: [ZKTAG_SIG_KEY], plugs: { [ZKTAG_SIG_KEY]: nonePlug } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [{ type: 'test-sig-ed25519-zktag', version: 1, data: { key_id: 'att1', sig: 'AAAA' } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
  assert.equal(out.reason, 'evidence_zktag_unavailable');
});

test('GAP 1: ctx.zktag is null at tier A and the presented string at tier B (plug-observed)', async () => {
  const seen = [];
  const spyPlug = {
    binds: { nonce: true, claim: true, scope: true }, // does NOT bind zktag
    linkability: 'none', tierCeiling: 'C',
    verify(_item, ctx) { seen.push(ctx.zktag); return { ok: true, valid: true, reason: 'spy_ok' }; },
  };
  const v = makeVerifier({ accept: ['spy/1'], plugs: { 'spy/1': spyPlug } });
  const cA = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  await v.verify(presentationFor(cA, { evidence: [{ type: 'spy', version: 1, data: {} }] }), { now: T0 });
  const cB = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const pB = presentationFor(cB, { evidence: [{ type: 'spy', version: 1, data: {} }] });
  await v.verify(pB, { now: T0 });
  assert.deepEqual(seen, [null, pB.zktag]);
});

test('GAP 1 registration: binds.zktag must be boolean; zktag:true with tierCeiling A is refused at boot', () => {
  const base = {
    binds: { nonce: true, claim: true, scope: true, zktag: true },
    linkability: 'none', tierCeiling: 'B', verify() { return { ok: true, valid: true }; },
  };
  assert.throws(
    () => makeVerifier({ plugs: { 'x/1': { ...base, binds: { ...base.binds, zktag: 'yes' } } } }),
    /binds\.zktag must be a boolean/,
  );
  assert.throws(
    () => makeVerifier({ plugs: { 'x/1': { ...base, tierCeiling: 'A' } } }),
    /tier A never carries a zktag/,
  );
  assert.doesNotThrow(() => makeVerifier({ plugs: { 'x/1': base } }));
  assert.doesNotThrow(() => makeVerifier({ plugs: { 'x/1': { ...base, binds: { nonce: true, claim: true, scope: true } } } }));
});

// ---------------------------------------------------------------------------
// Gap 2: per-tier evidence.require.
// ---------------------------------------------------------------------------

test('GAP 2: ONE instance — tier A bare passes while tier B without required evidence refuses', async () => {
  const v = makeVerifier({ require: { B: [ZKTAG_SIG_KEY] }, plugs: { [ZKTAG_SIG_KEY]: zktagPlug() } });

  // Tier A, bare (D27): nothing required at A.
  const cA = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const outA = await v.verify(presentationFor(cA), { now: T0 });
  assertInvariant(outA);
  assert.equal(outA.allowed, true);
  assert.equal(outA.reason, 'no-evidence-required');

  // Tier B, same verifier object, missing the required evidence (D30): real no.
  const cB = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const outB = await v.verify(presentationFor(cB), { now: T0 });
  assertInvariant(outB);
  assert.equal(outB.ok, true);
  assert.equal(outB.allowed, false);
  assert.equal(outB.reason, 'evidence_required_missing');

  // Tier B, same verifier object, WITH the evidence: passes.
  const cB2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const pB2 = presentationFor(cB2);
  pB2.evidence = [zktagEvidence(pB2.claim, cB2.nonce, pB2.zktag)];
  const outB2 = await v.verify(pB2, { now: T0 });
  assert.equal(outB2.allowed, true);
  assert.equal(outB2.reason, 'evidence-verified');
});

test('GAP 2 back-compat: the 0.2.0 plain-array require keeps instance-global semantics', async () => {
  const alwaysOk = {
    binds: { nonce: true, claim: true, scope: true },
    linkability: 'none', tierCeiling: 'C',
    verify() { return { ok: true, valid: true, reason: 'fixture_ok' }; },
  };
  const v = makeVerifier({ require: ['ok/1'], plugs: { 'ok/1': alwaysOk } });
  // Required at EVERY tier, including A — exactly the 0.2.0 behaviour.
  const cA = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const missing = await v.verify(presentationFor(cA), { now: T0 });
  assert.equal(missing.allowed, false);
  assert.equal(missing.reason, 'evidence_required_missing');

  const cA2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(cA2, { evidence: [{ type: 'ok', version: 1, data: {} }] });
  const out = await v.verify(p, { now: T0 });
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'evidence-verified');
});

test('GAP 2 config validation: bad tier keys, non-array values, unregistered names all throw at boot', () => {
  const plug = zktagPlug();
  assert.throws(() => makeVerifier({ require: { D: [ZKTAG_SIG_KEY] }, plugs: { [ZKTAG_SIG_KEY]: plug } }), /per-tier keys/);
  assert.throws(() => makeVerifier({ require: { B: ZKTAG_SIG_KEY }, plugs: { [ZKTAG_SIG_KEY]: plug } }), /must be an array/);
  assert.throws(() => makeVerifier({ require: { B: ['ghost/1'] }, plugs: { [ZKTAG_SIG_KEY]: plug } }), /no such plug/);
  assert.throws(() => makeVerifier({ require: 42 }), /array \(all tiers\) or a per-tier object/);
});

test('GAP 2: normalizeRequire (exported) — array, object, and undefined forms', () => {
  assert.deepEqual(normalizeRequire(undefined), { A: [], B: [], C: [] });
  assert.deepEqual(normalizeRequire(['x/1']), { A: ['x/1'], B: ['x/1'], C: ['x/1'] });
  assert.deepEqual(normalizeRequire({ B: ['x/1'] }), { A: [], B: ['x/1'], C: [] });
  assert.throws(() => normalizeRequire({ B: [1] }), TypeError);
});
