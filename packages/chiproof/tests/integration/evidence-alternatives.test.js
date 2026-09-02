// SPDX-License-Identifier: Apache-2.0
// D31/D36 (2026-09-01): `evidence.require` alternatives groups. An entry may
// be a registry-key string (all-of, unchanged since 0.2.0) or a non-empty
// array of registry-key strings — satisfied when at least one member is
// present and verifies. A present-but-invalid group member is NOT masked by
// another member passing (owner-escalated default: no masking, matching the
// existing all-of item-verification path).
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createVerifier, normalizeRequire } from '../../src/index.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';

const SECRET = randomBytes(32);
const T0 = 1_800_000_000_000;
const SCOPE = 'example.test';
const A = 'alt-a/1';
const B = 'alt-b/1';
const C = 'alt-c/1';

function makeVerifier(evidence, overrides = {}) {
  return createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
    allowInMemoryStore: true,
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
    spec: 'zkagent/1',
    tier: challenge.tier,
    claim: { over_threshold: true, threshold: challenge.threshold },
    challenge,
    evidence: [],
  };
  if (challenge.tier !== 'A') p.zktag = `zktag-${randomBytes(8).toString('hex')}`;
  return { ...p, ...extra };
}

/** Build a presented evidence item from a registry key like 'alt-a/1'. */
function item(key, data = {}) {
  const i = key.lastIndexOf('/');
  return { type: key.slice(0, i), version: Number(key.slice(i + 1)), data };
}

/** A plug whose verify() answer is controlled per call via `mode()`. */
function controllablePlug(mode) {
  return {
    binds: { nonce: true, claim: true, scope: true },
    linkability: 'none',
    tierCeiling: 'C',
    verify() {
      const m = mode();
      if (m === 'valid') return { ok: true, valid: true, reason: 'fixture_ok' };
      if (m === 'invalid') return { ok: true, valid: false, reason: 'fixture_bad' };
      throw new Error(`unknown mode ${m}`);
    },
  };
}

function assertInvariant(v) {
  if (v.ok === false) assert.equal(v.allowed, null, 'ok:false must force allowed:null');
  else assert.equal(typeof v.allowed, 'boolean');
}

// ---------------------------------------------------------------------------
// (a) group satisfied by its first member.
// ---------------------------------------------------------------------------

test('alternatives group: satisfied when only the first member is present and valid', async () => {
  const v = makeVerifier({
    require: [[A, B]],
    plugs: { [A]: controllablePlug(() => 'valid'), [B]: controllablePlug(() => 'valid') },
  });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor(c, { evidence: [item(A)] }), { now: T0 });
  assertInvariant(out);
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'evidence-verified');
  assert.deepEqual(out.evidence, [A], 'verdict records which group member was actually used');
});

// ---------------------------------------------------------------------------
// (b) group satisfied by its second member only.
// ---------------------------------------------------------------------------

test('alternatives group: satisfied when only the second member is present and valid', async () => {
  const v = makeVerifier({
    require: [[A, B]],
    plugs: { [A]: controllablePlug(() => 'valid'), [B]: controllablePlug(() => 'valid') },
  });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor(c, { evidence: [item(B)] }), { now: T0 });
  assertInvariant(out);
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'evidence-verified');
  assert.deepEqual(out.evidence, [B], 'verdict records which group member was actually used');
});

// ---------------------------------------------------------------------------
// (c) neither member present -> evidence_required_missing.
// ---------------------------------------------------------------------------

test('alternatives group: neither member present is evidence_required_missing', async () => {
  const v = makeVerifier({
    require: [[A, B]],
    plugs: { [A]: controllablePlug(() => 'valid'), [B]: controllablePlug(() => 'valid') },
  });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor(c), { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'evidence_required_missing');
});

// ---------------------------------------------------------------------------
// (d) mixed string + group requirement: both the plain string AND one member
// of the group must be satisfied (top level stays all-of; only the group
// entry itself is any-of).
// ---------------------------------------------------------------------------

test('mixed requirement: a plain string entry alongside an alternatives group -- both must be satisfied', async () => {
  const v = makeVerifier({
    require: [C, [A, B]],
    plugs: {
      [A]: controllablePlug(() => 'valid'),
      [B]: controllablePlug(() => 'valid'),
      [C]: controllablePlug(() => 'valid'),
    },
  });

  // Group member present, but C (plain string) missing -> still required_missing.
  const c1 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const missingC = await v.verify(presentationFor(c1, { evidence: [item(A)] }), { now: T0 });
  assert.equal(missingC.allowed, false);
  assert.equal(missingC.reason, 'evidence_required_missing');

  // C present, but neither A nor B present -> still required_missing.
  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const missingGroup = await v.verify(presentationFor(c2, { evidence: [item(C)] }), { now: T0 });
  assert.equal(missingGroup.allowed, false);
  assert.equal(missingGroup.reason, 'evidence_required_missing');

  // Both satisfied (C, plus B for the group) -> allowed, verdict lists both used keys.
  const c3 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const both = await v.verify(presentationFor(c3, { evidence: [item(C), item(B)] }), { now: T0 });
  assert.equal(both.allowed, true);
  assert.deepEqual(both.evidence.sort(), [B, C].sort());
});

// ---------------------------------------------------------------------------
// (e) a group member is present but fails verification -- NOT masked by
// another (unpresented, or even presented) member passing.
// ---------------------------------------------------------------------------

test('alternatives group: a present-but-invalid member is a real no, not masked by the group being satisfiable', async () => {
  const v = makeVerifier({
    require: [[A, B]],
    plugs: { [A]: controllablePlug(() => 'invalid'), [B]: controllablePlug(() => 'valid') },
  });
  // Only the invalid member A is presented: this is the plain "required
  // evidence present but wrong" case -- a real no, same as pre-0.5.0 all-of.
  const c1 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out1 = await v.verify(presentationFor(c1, { evidence: [item(A)] }), { now: T0 });
  assertInvariant(out1);
  assert.equal(out1.ok, true);
  assert.equal(out1.allowed, false);
  assert.equal(out1.reason, 'fixture_bad');

  // BOTH members presented -- A invalid, B valid. No masking: A's failure
  // still fails the whole presentation even though B alone would satisfy
  // the group. (Escalated design choice -- see chiproof CHANGELOG/report.)
  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out2 = await v.verify(presentationFor(c2, { evidence: [item(A), item(B)] }), { now: T0 });
  assertInvariant(out2);
  assert.equal(out2.ok, true);
  assert.equal(out2.allowed, false, 'a failing presented member is never masked by a passing one');
  assert.equal(out2.reason, 'fixture_bad');
});

// ---------------------------------------------------------------------------
// Config validation and normalizeRequire shape.
// ---------------------------------------------------------------------------

test('alternatives group config validation: empty array, non-string members, and unregistered members all throw at boot', () => {
  assert.throws(() => makeVerifier({ require: [[]], plugs: { [A]: controllablePlug(() => 'valid') } }), /alternatives group/);
  assert.throws(() => makeVerifier({ require: [[A, 1]], plugs: { [A]: controllablePlug(() => 'valid') } }), /alternatives group/);
  assert.throws(() => makeVerifier({ require: [[A, 'ghost/1']], plugs: { [A]: controllablePlug(() => 'valid') } }), /no such plug/);
  assert.doesNotThrow(() => makeVerifier({ require: [[A, B]], plugs: { [A]: controllablePlug(() => 'valid'), [B]: controllablePlug(() => 'valid') } }));
});

test('alternatives group works in the per-tier {A?, B?, C?} require shape too', async () => {
  const v = makeVerifier({
    require: { B: [[A, B]] },
    plugs: { [A]: controllablePlug(() => 'valid'), [B]: controllablePlug(() => 'valid') },
  });
  const cA = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const outA = await v.verify(presentationFor(cA), { now: T0 });
  assert.equal(outA.allowed, true, 'tier A stays bare -- nothing required there');

  const cB = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const missing = await v.verify(presentationFor(cB), { now: T0 });
  assert.equal(missing.reason, 'evidence_required_missing');

  const cB2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const ok = await v.verify(presentationFor(cB2, { evidence: [item(B)] }), { now: T0 });
  assert.equal(ok.allowed, true);
  assert.deepEqual(ok.evidence, [B]);
});

test('normalizeRequire (exported): array entries may be a string or an alternatives-group array', () => {
  assert.deepEqual(normalizeRequire([A]), { A: [A], B: [A], C: [A] });
  assert.deepEqual(normalizeRequire([[A, B]]), { A: [[A, B]], B: [[A, B]], C: [[A, B]] });
  assert.deepEqual(normalizeRequire({ B: [C, [A, B]] }), { A: [], B: [C, [A, B]], C: [] });
  assert.throws(() => normalizeRequire([[]]), TypeError);
  assert.throws(() => normalizeRequire([[1, 2]]), TypeError);
  assert.throws(() => normalizeRequire([42]), TypeError);
});
