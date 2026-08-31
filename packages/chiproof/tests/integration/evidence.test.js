import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes, generateKeyPairSync, sign as edSign } from 'node:crypto';
import { createVerifier } from '../../src/index.js';
import { signedReceipt, receiptMessage } from '../../src/plugs/signed-receipt.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';
import {
  alwaysThrows, alwaysRejects, deviceClass, cannotBindNonce, alwaysValid,
  cannotCheckPlug, returnsGarbage, ceilingB,
} from '../fixtures/adversarial-plugs.js';

// Runtime-generated key material only; nothing in the tree.
const SECRET = randomBytes(32);
const T0 = 1_800_000_000_000;
const signer = generateKeyPairSync('ed25519');
const stranger = generateKeyPairSync('ed25519');
const RECEIPT = 'signed-receipt/1';
const SCOPE = 'example.test';

function makeVerifier(evidence, overrides = {}) {
  return createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
    allowInMemoryStore: true,
    challengeSecret: SECRET,
    scopeDomain: 'example.test',
    threshold: 18,
    tiers: { max: 'C' },
    evidence,
    ...overrides,
  });
}

function receiptPlug() {
  return signedReceipt({ keys: [{ key_id: 'k1', pubkey: signer.publicKey }] });
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

/** A receipt signed by `privateKey` over the given claim and nonce. */
function receipt(claim, nonce, privateKey = signer.privateKey, keyId = 'k1', scope = SCOPE) {
  const sig = edSign(null, receiptMessage(claim, nonce, scope), privateKey).toString('base64');
  return { type: 'signed-receipt', version: 1, data: { key_id: keyId, sig } };
}

function item(type, version = 1, data = {}) {
  return { type, version, data };
}

function assertInvariant(v) {
  if (v.ok === false) assert.equal(v.allowed, null, 'ok:false must force allowed:null');
  else assert.equal(typeof v.allowed, 'boolean');
}

// ---------------------------------------------------------------------------
// Registration-time contract.
// ---------------------------------------------------------------------------

test('registration: a plug that cannot bind the nonce is refused at boot, never at verify', () => {
  assert.throws(() => makeVerifier({ plugs: { 'nobind/1': cannotBindNonce } }), /binds\.nonce/);
});

test('registration: bad linkability / ceiling / verify shapes are refused at boot', () => {
  const base = { binds: { nonce: true, claim: true, scope: true }, linkability: 'none', tierCeiling: 'C', verify() {} };
  assert.throws(() => makeVerifier({ plugs: { 'x/1': { ...base, linkability: 'weak' } } }), TypeError);
  assert.throws(() => makeVerifier({ plugs: { 'x/1': { ...base, tierCeiling: 'D' } } }), TypeError);
  assert.throws(() => makeVerifier({ plugs: { 'x/1': { ...base, verify: 'nope' } } }), TypeError);
  assert.doesNotThrow(() => makeVerifier({ plugs: { 'x/1': base } }));
});

test('registration: require/accept must name registered plugs', () => {
  assert.throws(() => makeVerifier({ require: [RECEIPT] }), /no such plug/);
  assert.throws(() => makeVerifier({ accept: ['ghost/1'], plugs: { [RECEIPT]: receiptPlug() } }), /no such plug/);
  assert.doesNotThrow(() => makeVerifier({ require: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } }));
});

test('signed-receipt registration needs a non-empty, well-formed key list', () => {
  assert.throws(() => signedReceipt({}), TypeError);
  assert.throws(() => signedReceipt({ keys: [] }), TypeError);
  assert.throws(() => signedReceipt({ keys: [{ key_id: 'a', pubkey: signer.publicKey }, { key_id: 'a', pubkey: signer.publicKey }] }), /duplicate/);
});

// ---------------------------------------------------------------------------
// Matrix 11: required evidence missing.
// ---------------------------------------------------------------------------

test('MATRIX 11: a required receipt that is absent refuses; the same presentation with it passes with reason evidence-verified', async () => {
  const v = makeVerifier({ require: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const c1 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const missing = await v.verify(presentationFor(c1), { now: T0 });
  assertInvariant(missing);
  assert.equal(missing.allowed, false);
  assert.equal(missing.reason, 'evidence_required_missing');

  const c2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c2);
  p.evidence = [receipt(p.claim, c2.nonce)];
  const ok = await v.verify(p, { now: T0 });
  assert.equal(ok.allowed, true);
  assert.equal(ok.reason, 'evidence-verified');
});

test('an unknown evidence type is ignored; an accepted type is checked only when present', async () => {
  const v = makeVerifier({ accept: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const c1 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor(c1, { evidence: [item('mystery', 9)] }), { now: T0 });
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'no-evidence-required');

  const c2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c2);
  p.evidence = [receipt(p.claim, c2.nonce, stranger.privateKey)];
  const bad = await v.verify(p, { now: T0 });
  assert.equal(bad.reason, 'receipt_signature_invalid', 'an accepted type that IS present is checked');
});

// ---------------------------------------------------------------------------
// Matrix 12: evidence bound to a different nonce.
// ---------------------------------------------------------------------------

test('MATRIX 12: a receipt signed over another challenge\'s nonce is refused; over this nonce it passes', async () => {
  const v = makeVerifier({ require: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const other = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const c1 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p1 = presentationFor(c1);
  p1.evidence = [receipt(p1.claim, other.nonce)];
  const bad = await v.verify(p1, { now: T0 });
  assertInvariant(bad);
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'receipt_signature_invalid');

  const c2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p2 = presentationFor(c2);
  p2.evidence = [receipt(p2.claim, c2.nonce)];
  assert.equal((await v.verify(p2, { now: T0 })).allowed, true);
});

// ---------------------------------------------------------------------------
// Matrix 13: evidence for a different claim/threshold.
// ---------------------------------------------------------------------------

test('MATRIX 13: a receipt over a threshold-21 claim is refused against an 18 presentation; over the 18 claim it passes', async () => {
  const v = makeVerifier({ require: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const c1 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p1 = presentationFor(c1);
  p1.evidence = [receipt({ over_threshold: true, threshold: 21 }, c1.nonce)];
  const bad = await v.verify(p1, { now: T0 });
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'receipt_signature_invalid');

  const c2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p2 = presentationFor(c2);
  p2.evidence = [receipt(p2.claim, c2.nonce)];
  assert.equal((await v.verify(p2, { now: T0 })).allowed, true);
});

test('signed-receipt: unknown key_id, wrong signer, and malformed data are all real noes', async () => {
  const v = makeVerifier({ require: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const cases = [
    [(claim, nonce) => receipt(claim, nonce, signer.privateKey, 'k-unknown'), 'receipt_unknown_key'],
    [(claim, nonce) => receipt(claim, nonce, stranger.privateKey), 'receipt_signature_invalid'],
    [() => item('signed-receipt', 1, { key_id: 'k1', sig: '' }), 'receipt_malformed'],
    [() => item('signed-receipt', 1, null), 'receipt_malformed'],
    [() => item('signed-receipt', 1, { key_id: 'k1', sig: '!!not-base64-of-a-signature' }), 'receipt_signature_invalid'],
  ];
  for (const [make, reason] of cases) {
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    p.evidence = [make(p.claim, c.nonce)];
    const out = await v.verify(p, { now: T0 });
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
    assert.equal(out.reason, reason);
  }
});

// ---------------------------------------------------------------------------
// Matrix 14: plug throws -> ok:false, allowed:null.
// ---------------------------------------------------------------------------

test('MATRIX 14: a throwing (or rejecting, or garbage-returning) plug is ok:false/allowed:null; a working plug passes', async () => {
  for (const [name, plug] of [['throws', alwaysThrows], ['rejects', alwaysRejects], ['garbage', returnsGarbage]]) {
    const v = makeVerifier({ require: ['adv/1'], plugs: { 'adv/1': plug } });
    const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
    const out = await v.verify(presentationFor(c, { evidence: [item('adv')] }), { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, false, name);
    assert.equal(out.allowed, null, name);
    assert.equal(out.reason, 'evidence_plug_failed', name);
  }
  const v = makeVerifier({ require: ['adv/1'], plugs: { 'adv/1': alwaysValid() } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const ok = await v.verify(presentationFor(c, { evidence: [item('adv')] }), { now: T0 });
  assert.equal(ok.allowed, true);
  assert.equal(ok.reason, 'evidence-verified');
});

test('a plug reporting ok:false propagates as ok:false with its own reason', async () => {
  const v = makeVerifier({ require: ['adv/1'], plugs: { 'adv/1': cannotCheckPlug } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor(c, { evidence: [item('adv')] }), { now: T0 });
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
  assert.equal(out.reason, 'fixture_backend_down');
});

test('MATRIX 14b: an ok:false required plug beats a valid one -- allowed:null wins, order-independent', async () => {
  const v = makeVerifier({
    require: ['adv/1', 'ok/1'],
    plugs: { 'adv/1': cannotCheckPlug, 'ok/1': alwaysValid() },
  });

  const c1 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const brokenFirst = await v.verify(
    presentationFor(c1, { evidence: [item('adv'), item('ok')] }),
    { now: T0 },
  );
  assertInvariant(brokenFirst);
  assert.equal(brokenFirst.ok, false, 'broken-first order');
  assert.equal(brokenFirst.allowed, null, 'broken-first order');
  assert.equal(brokenFirst.reason, 'fixture_backend_down');

  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const brokenLast = await v.verify(
    presentationFor(c2, { evidence: [item('ok'), item('adv')] }),
    { now: T0 },
  );
  assertInvariant(brokenLast);
  assert.equal(brokenLast.ok, false, 'broken-last order');
  assert.equal(brokenLast.allowed, null, 'broken-last order');
  assert.equal(brokenLast.reason, 'fixture_backend_down');
});

// ---------------------------------------------------------------------------
// Matrix 15: device-class evidence in tier A -> refused.
// ---------------------------------------------------------------------------

test('MATRIX 15: device-class evidence is refused at tier A and accepted at tier B', async () => {
  const v = makeVerifier({ accept: ['dev/1'], plugs: { 'dev/1': deviceClass } });
  const cA = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const atA = await v.verify(presentationFor(cA, { evidence: [item('dev')] }), { now: T0 });
  assertInvariant(atA);
  assert.equal(atA.allowed, false);
  assert.equal(atA.reason, 'evidence_forbidden_at_tier_a');

  const cB = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const atB = await v.verify(presentationFor(cB, { evidence: [item('dev')] }), { now: T0 });
  assert.equal(atB.allowed, true);
});

test('signer-class evidence (signed-receipt) is likewise refused at tier A even when valid', async () => {
  const v = makeVerifier({ accept: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [receipt(p.claim, c.nonce)];
  const out = await v.verify(p, { now: T0 });
  assert.equal(out.reason, 'evidence_forbidden_at_tier_a');
});

test('evidence above its plug ceiling is refused; at the ceiling it passes', async () => {
  const issuer = generateKeyPairSync('ed25519');
  const v = makeVerifier({ accept: ['cap/1'], plugs: { 'cap/1': ceilingB } }, {
    trustedChallengeIssuers: [{ pubkey: issuer.publicKey, key_id: 'iss', maxTier: 'C' }],
  });
  // Tier C challenges must be signed (D20); sign this one so the ceiling is what refuses.
  const cC = v.issueChallenge({ tier: 'C', ttlMs: 60_000, now: T0, issuer: { privateKey: issuer.privateKey, key_id: 'iss' } });
  const atC = await v.verify(presentationFor(cC, { evidence: [item('cap')] }), { now: T0 });
  assert.equal(atC.reason, 'evidence_tier_exceeds_plug_ceiling');
  const cB = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  assert.equal((await v.verify(presentationFor(cB, { evidence: [item('cap')] }), { now: T0 })).allowed, true);
});

// ---------------------------------------------------------------------------
// Expiry precedence (ruling 5) and the other plumbing.
// ---------------------------------------------------------------------------

test('evidence expiry: a plug result dated before now is evidence_expired; dated after now passes', async () => {
  const stale = makeVerifier({ require: ['adv/1'], plugs: { 'adv/1': alwaysValid({ expiresAt: T0 - 1 }) } });
  const c1 = stale.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await stale.verify(presentationFor(c1, { evidence: [item('adv')] }), { now: T0 });
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'evidence_expired');

  const fresh = makeVerifier({ require: ['adv/1'], plugs: { 'adv/1': alwaysValid({ expiresAt: T0 + 1 }) } });
  const c2 = fresh.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  assert.equal((await fresh.verify(presentationFor(c2, { evidence: [item('adv')] }), { now: T0 })).allowed, true);
});

test('a malformed evidence item (non-object, or missing type/version) is a real no', async () => {
  const v = makeVerifier({ plugs: { 'adv/1': alwaysValid() } });
  for (const bad of ['adv', 42, null, { version: 1 }, { type: 'adv', version: {} }]) {
    const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
    const out = await v.verify(presentationFor(c, { evidence: [bad] }), { now: T0 });
    assert.equal(out.ok, true);
    assert.equal(out.reason, 'evidence_item_malformed', JSON.stringify(bad));
  }
});

test('the plug receives the §4 ctx: nonce, claim, tier, now, trustedClients', async () => {
  let seen;
  const spy = { binds: { nonce: true, claim: true, scope: true }, linkability: 'none', tierCeiling: 'C',
    verify(_item, ctx) { seen = ctx; return { ok: true, valid: true, reason: 'x' }; } };
  const v = makeVerifier({ require: ['spy/1'], plugs: { 'spy/1': spy } }, { trustedClients: [{ package: 'p', certDigest: 'd' }], scopeDomain: 'example.test' });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c, { evidence: [item('spy')] });
  await v.verify(p, { now: T0 });
  assert.equal(seen.nonce, c.nonce);
  assert.deepEqual(seen.claim, p.claim);
  assert.equal(seen.tier, 'A');
  assert.equal(seen.now, T0);
  assert.equal(seen.scopeDomain, 'example.test');
  assert.deepEqual(seen.trustedClients, [{ package: 'p', certDigest: 'd' }]);
  assert.equal(seen.maxScanAge, null, 'unset max_scan_age reaches the plug as null');

  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0, max_scan_age: 5000 });
  await v.verify(presentationFor(c2, { evidence: [item('spy')] }), { now: T0 });
  assert.equal(seen.maxScanAge, 5000, 'challenge.max_scan_age reaches the plug as ctx.maxScanAge');
});

test('a malformed challenge.max_scan_age is a real no', async () => {
  const v = makeVerifier({});
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor({ ...c, max_scan_age: 'soon' }), { now: T0 });
  assert.equal(out.reason, 'challenge_malformed');
});

test('ruling 4: createVerifier().issueChallenge pins threshold to config.threshold', () => {
  const v = makeVerifier({});
  assert.equal(v.issueChallenge({ tier: 'A', ttlMs: 1000, now: T0 }).threshold, 18);
  assert.equal(v.issueChallenge({ tier: 'A', ttlMs: 1000, now: T0, threshold: 18 }).threshold, 18);
  assert.throws(() => v.issueChallenge({ tier: 'A', ttlMs: 1000, now: T0, threshold: 21 }), TypeError);
});

test('signed-receipt binds scope: a receipt over another scopeDomain is refused; over ours it passes', async () => {
  const v = makeVerifier({ require: [RECEIPT], plugs: { [RECEIPT]: receiptPlug() } });
  const c1 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p1 = presentationFor(c1);
  p1.evidence = [receipt(p1.claim, c1.nonce, signer.privateKey, 'k1', 'other.example')];
  const bad = await v.verify(p1, { now: T0 });
  assert.equal(bad.reason, 'receipt_signature_invalid');

  const c2 = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p2 = presentationFor(c2);
  p2.evidence = [receipt(p2.claim, c2.nonce)];
  const good = await v.verify(p2, { now: T0 });
  assert.equal(good.allowed, true);
  assert.deepEqual(good.evidence, [RECEIPT], 'the verdict lists the evidence actually verified');
});

test('verdict.evidence: [] in bare mode; only checked types listed when several are presented', async () => {
  const v = makeVerifier({ accept: [RECEIPT], plugs: { [RECEIPT]: receiptPlug(), 'adv/1': alwaysValid() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [item('mystery'), item('adv'), receipt(p.claim, c.nonce)];
  const out = await v.verify(p, { now: T0 });
  assert.equal(out.allowed, true);
  assert.deepEqual(out.evidence, [RECEIPT], 'unknown and unlisted-but-registered types are not claimed as verified');
});

test('createVerifier requires scopeDomain', () => {
  assert.throws(() => makeVerifier({}, { scopeDomain: undefined }), /scopeDomain/);
});

// ---------------------------------------------------------------------------
// F4 bounds: enforced before any plug runs (spy counts verify() calls).
// ---------------------------------------------------------------------------

function spyPlug() {
  const calls = [];
  return {
    calls,
    plug: { binds: { nonce: true, claim: true, scope: true }, linkability: 'none', tierCeiling: 'C',
      verify(it) { calls.push(it); return { ok: true, valid: true, reason: 'spy_ok' }; } },
  };
}

test('F4 duplicate: the same type/version twice is evidence_duplicate and no plug runs; once passes', async () => {
  const { calls, plug } = spyPlug();
  const v = makeVerifier({ accept: ['spy/1'], plugs: { 'spy/1': plug } });
  const c1 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const dup = await v.verify(presentationFor(c1, { evidence: [item('spy'), item('spy')] }), { now: T0 });
  assert.equal(dup.allowed, false);
  assert.equal(dup.reason, 'evidence_duplicate');
  assert.equal(calls.length, 0, 'no plug may run on a refused presentation');

  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const single = await v.verify(presentationFor(c2, { evidence: [item('spy')] }), { now: T0 });
  assert.equal(single.allowed, true);
  assert.equal(calls.length, 1);
});

test('F4 maxItems: default 4 -- five items refused before any plug runs, four pass; configurable to 1', async () => {
  const { calls, plug } = spyPlug();
  const v = makeVerifier({ accept: ['spy/1'], plugs: { 'spy/1': plug } });
  const five = ['a', 'b', 'c', 'd', 'e'].map((t) => item(t));
  const c1 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const tooMany = await v.verify(presentationFor(c1, { evidence: [...five.slice(0, 4), item('spy')] }), { now: T0 });
  assert.equal(tooMany.reason, 'evidence_too_many');
  assert.equal(calls.length, 0);

  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const four = await v.verify(presentationFor(c2, { evidence: [...five.slice(0, 3), item('spy')] }), { now: T0 });
  assert.equal(four.allowed, true);
  assert.equal(calls.length, 1);

  const strict = makeVerifier({ accept: ['spy/1'], plugs: { 'spy/1': spyPlug().plug }, maxItems: 1 });
  const c3 = strict.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  assert.equal((await strict.verify(presentationFor(c3, { evidence: [item('x'), item('spy')] }), { now: T0 })).reason, 'evidence_too_many');
  const c4 = strict.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  assert.equal((await strict.verify(presentationFor(c4, { evidence: [item('spy')] }), { now: T0 })).allowed, true);
});

test('F4 maxItemBytes: an oversized item is evidence_too_large before any plug runs; within the cap passes', async () => {
  const { calls, plug } = spyPlug();
  const v = makeVerifier({ accept: ['spy/1'], plugs: { 'spy/1': plug }, maxItemBytes: 200 });
  const c1 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const big = await v.verify(presentationFor(c1, { evidence: [item('spy', 1, { blob: 'x'.repeat(300) })] }), { now: T0 });
  assert.equal(big.reason, 'evidence_too_large');
  assert.equal(calls.length, 0);

  const c2 = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const small = await v.verify(presentationFor(c2, { evidence: [item('spy', 1, { blob: 'x'.repeat(50) })] }), { now: T0 });
  assert.equal(small.allowed, true);
  assert.equal(calls.length, 1);
});

test('F4 config: maxItems / maxItemBytes must be integers >= 1', () => {
  assert.throws(() => makeVerifier({ maxItems: 0 }), TypeError);
  assert.throws(() => makeVerifier({ maxItems: 1.5 }), TypeError);
  assert.throws(() => makeVerifier({ maxItemBytes: -1 }), TypeError);
  assert.doesNotThrow(() => makeVerifier({ maxItems: 1, maxItemBytes: 1 }));
});

// F2: recognised-but-unlisted items are still subject to linkability/ceiling.
test('F2: a registered but unlisted device-class item is refused at tier A, ignored (and allowed) at tier B', async () => {
  const v = makeVerifier({ plugs: { 'dev/1': deviceClass } }); // registered, in neither require nor accept
  const cA = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const atA = await v.verify(presentationFor(cA, { evidence: [item('dev')] }), { now: T0 });
  assert.equal(atA.reason, 'evidence_forbidden_at_tier_a');

  const cB = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const atB = await v.verify(presentationFor(cB, { evidence: [item('dev')] }), { now: T0 });
  assert.equal(atB.allowed, true);
  assert.deepEqual(atB.evidence, [], 'unlisted means not verified and not claimed');
});

// F1: plug warnings ride through onto the verdict without changing it.
test('F1: plug warnings are passed through as verdict.warnings; absent when none', async () => {
  const warner = { binds: { nonce: true, claim: true, scope: true }, linkability: 'none', tierCeiling: 'C',
    verify() { return { ok: true, valid: true, reason: 'ok', warnings: ['tmpdir_cleanup_failed'] }; } };
  const v = makeVerifier({ require: ['w/1'], plugs: { 'w/1': warner } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const out = await v.verify(presentationFor(c, { evidence: [item('w')] }), { now: T0 });
  assert.equal(out.allowed, true);
  assert.deepEqual(out.warnings, ['tmpdir_cleanup_failed']);

  const quiet = makeVerifier({ require: ['adv/1'], plugs: { 'adv/1': alwaysValid() } });
  const c2 = quiet.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  assert.equal('warnings' in await quiet.verify(presentationFor(c2, { evidence: [item('adv')] }), { now: T0 }), false);
});
