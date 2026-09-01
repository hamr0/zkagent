// SPDX-License-Identifier: Apache-2.0
// `sig-ed25519/1` / `sig-p256/1` — the D30 attester-key evidence plug family
// (PRD §6.2 items 1/9/11, FR12, D30; P-256 amended 2026-08-31).
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  randomBytes, generateKeyPairSync, sign as edSign, sign as ecSign,
} from 'node:crypto';
import { createVerifier } from '../../src/index.js';
import {
  sigEd25519, sigEd25519Message, sigP256, sigP256Message,
} from '../../src/plugs/attester-sig.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';

const SECRET = randomBytes(32);
const T0 = 1_800_000_000_000;
const SCOPE = 'example.test';

const edKeys = generateKeyPairSync('ed25519');
const edStranger = generateKeyPairSync('ed25519');
const ecKeys = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const ecStranger = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });

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

function edEvidence(claim, nonce, zktag, {
  privateKey = edKeys.privateKey, keyId = 'att1', scope = SCOPE,
} = {}) {
  const sig = edSign(null, sigEd25519Message(claim, nonce, scope, zktag), privateKey).toString('base64');
  return { type: 'sig-ed25519', version: 1, data: { key_id: keyId, sig } };
}

function ecEvidence(claim, nonce, zktag, {
  privateKey = ecKeys.privateKey, keyId = 'att1', scope = SCOPE,
} = {}) {
  const sig = ecSign('sha256', sigP256Message(claim, nonce, scope, zktag), privateKey).toString('base64');
  return { type: 'sig-p256', version: 1, data: { key_id: keyId, sig } };
}

function edPlug(keys = [{ key_id: 'att1', pubkey: edKeys.publicKey }]) {
  return sigEd25519({ keys });
}

function ecPlug(keys = [{ key_id: 'att1', pubkey: ecKeys.publicKey }]) {
  return sigP256({ keys });
}

function assertInvariant(v) {
  if (v.ok === false) assert.equal(v.allowed, null, 'ok:false must force allowed:null');
  else assert.equal(typeof v.allowed, 'boolean');
}

// ---------------------------------------------------------------------------
// Happy path, per algorithm.
// ---------------------------------------------------------------------------

test('sig-ed25519/1: happy path verifies end-to-end at tier B', async () => {
  const v = makeVerifier({ require: { B: ['sig-ed25519/1'] }, plugs: { 'sig-ed25519/1': edPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [edEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'evidence-verified');
  assert.deepEqual(out.evidence, ['sig-ed25519/1']);
});

test('sig-p256/1: happy path verifies end-to-end at tier B', async () => {
  const v = makeVerifier({ require: { B: ['sig-p256/1'] }, plugs: { 'sig-p256/1': ecPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [ecEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'evidence-verified');
  assert.deepEqual(out.evidence, ['sig-p256/1']);
});

// ---------------------------------------------------------------------------
// Cross-algorithm replay: the domain prefix must make one algorithm's
// signature unusable as the other's, even over otherwise-identical bytes.
// ---------------------------------------------------------------------------

test('CROSS-ALG: a valid sig-ed25519/1 signature presented as sig-p256/1 fails', async () => {
  // Same claim/nonce/scope/zktag, but Ed25519 signs sha256(preimage) while
  // P-256 verification hashes the raw preimage itself — the bytes an
  // Ed25519 signature covers are never what sig-p256/1 verifies against,
  // and even if they were, the pinned key here is an EC key, not Ed25519.
  const v = makeVerifier({ accept: ['sig-p256/1'], plugs: { 'sig-p256/1': ecPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  const edSig = edSign(null, sigEd25519Message(p.claim, c.nonce, SCOPE, p.zktag), edKeys.privateKey).toString('base64');
  p.evidence = [{ type: 'sig-p256', version: 1, data: { key_id: 'att1', sig: edSig } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true, 'a checked-and-failed answer, not could-not-check');
  assert.equal(out.allowed, false);
});

test('CROSS-ALG: a valid sig-p256/1 signature presented as sig-ed25519/1 fails', async () => {
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': edPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  const ecSig = ecSign('sha256', sigP256Message(p.claim, c.nonce, SCOPE, p.zktag), ecKeys.privateKey).toString('base64');
  p.evidence = [{ type: 'sig-ed25519', version: 1, data: { key_id: 'att1', sig: ecSig } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
});

// ---------------------------------------------------------------------------
// Wrong nonce / claim / scopeDomain / zktag — each fails, paired with a
// control that differs only in the one field under test.
// ---------------------------------------------------------------------------

for (const [label, plugFactory, evidenceFactory, requireKey] of [
  ['sig-ed25519/1', edPlug, edEvidence, 'sig-ed25519/1'],
  ['sig-p256/1', ecPlug, ecEvidence, 'sig-p256/1'],
]) {
  test(`${label}: control — correct nonce/claim/scope/zktag passes`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    p.evidence = [evidenceFactory(p.claim, c.nonce, p.zktag)];
    const out = await v.verify(p, { now: T0 });
    assert.equal(out.allowed, true);
  });

  test(`${label}: wrong nonce fails (evidence signed under a DIFFERENT live nonce)`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const cVictim = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    assert.notEqual(cVictim.nonce, c.nonce);
    p.evidence = [evidenceFactory(p.claim, cVictim.nonce, p.zktag)];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
  });

  test(`${label}: wrong claim fails (evidence signed over a different threshold claim)`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    const otherClaim = { over_threshold: true, threshold: 21 };
    p.evidence = [evidenceFactory(otherClaim, c.nonce, p.zktag)];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
  });

  test(`${label}: wrong scopeDomain fails (evidence signed for a different verifier's scope)`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    p.evidence = [evidenceFactory(p.claim, c.nonce, p.zktag, { scope: 'someone-else.test' })];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
  });

  test(`${label}: wrong zktag fails (zktag-swap — evidence signed for a victim's zktag)`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    const victimZktag = `zktag-victim-${randomBytes(8).toString('hex')}`;
    assert.notEqual(p.zktag, victimZktag);
    p.evidence = [evidenceFactory(p.claim, c.nonce, victimZktag)];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
  });

  test(`${label}: unknown key_id fails`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    const ev = evidenceFactory(p.claim, c.nonce, p.zktag, { keyId: 'ghost' });
    p.evidence = [ev];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
    assert.equal(out.reason, 'sig_unknown_key');
  });

  test(`${label}: malformed base64 sig fails (garbage bytes never verify, whatever the router reason)`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    p.evidence = [{ type: requireKey.split('/')[0], version: 1, data: { key_id: 'att1', sig: '***not-base64***' } }];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
    assert.match(out.reason, /^sig_(malformed|invalid)$/);
  });

  test(`${label}: missing fields (no sig) fails`, async () => {
    const v = makeVerifier({ require: { B: [requireKey] }, plugs: { [requireKey]: plugFactory() } });
    const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
    const p = presentationFor(c);
    p.evidence = [{ type: requireKey.split('/')[0], version: 1, data: { key_id: 'att1' } }];
    const out = await v.verify(p, { now: T0 });
    assertInvariant(out);
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
    assert.equal(out.reason, 'sig_malformed');
  });
}

// ---------------------------------------------------------------------------
// Wrong key type pinned for the plug.
// ---------------------------------------------------------------------------

test('sig-ed25519/1: an EC pubkey pinned for this plug cannot verify (wrong key type)', async () => {
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': sigEd25519({ keys: [{ key_id: 'att1', pubkey: ecKeys.publicKey }] }) } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  // A genuine Ed25519 signature (from the Ed25519 key, not the pinned EC key)
  // will not verify against the pinned EC pubkey either way.
  p.evidence = [edEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
});

test('sig-p256/1: an Ed25519 pubkey pinned for this plug cannot verify (wrong key type)', async () => {
  const v = makeVerifier({ accept: ['sig-p256/1'], plugs: { 'sig-p256/1': sigP256({ keys: [{ key_id: 'att1', pubkey: edKeys.publicKey }] }) } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [ecEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
});

// ---------------------------------------------------------------------------
// Stranger key: a differently-generated key of the SAME algorithm/curve
// must not verify either (control: the real key passes).
// ---------------------------------------------------------------------------

test('sig-ed25519/1: signature from an unpinned Ed25519 stranger key fails', async () => {
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': edPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [edEvidence(p.claim, c.nonce, p.zktag, { privateKey: edStranger.privateKey })];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'sig_invalid');
});

test('sig-p256/1: signature from an unpinned P-256 stranger key fails', async () => {
  const v = makeVerifier({ accept: ['sig-p256/1'], plugs: { 'sig-p256/1': ecPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [ecEvidence(p.claim, c.nonce, p.zktag, { privateKey: ecStranger.privateKey })];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'sig_invalid');
});

// ---------------------------------------------------------------------------
// Registration: empty/duplicate key list rejected, same as signed-receipt/1.
// ---------------------------------------------------------------------------

test('sig-ed25519/1 registration: empty keys list throws', () => {
  assert.throws(() => sigEd25519({ keys: [] }), /non-empty keys list/);
  assert.throws(() => sigEd25519({}), /non-empty keys list/);
});

test('sig-p256/1 registration: empty keys list throws', () => {
  assert.throws(() => sigP256({ keys: [] }), /non-empty keys list/);
  assert.throws(() => sigP256({}), /non-empty keys list/);
});

test('sig-ed25519/1 registration: duplicate key_id throws', () => {
  assert.throws(
    () => sigEd25519({ keys: [{ key_id: 'a', pubkey: edKeys.publicKey }, { key_id: 'a', pubkey: edStranger.publicKey }] }),
    /duplicate key_id a/,
  );
});

test('sig-p256/1 registration: duplicate key_id throws', () => {
  assert.throws(
    () => sigP256({ keys: [{ key_id: 'a', pubkey: ecKeys.publicKey }, { key_id: 'a', pubkey: ecStranger.publicKey }] }),
    /duplicate key_id a/,
  );
});

test('sig-ed25519/1 registration: missing key_id/pubkey throws', () => {
  assert.throws(() => sigEd25519({ keys: [{ pubkey: edKeys.publicKey }] }), /non-empty key_id/);
  assert.throws(() => sigEd25519({ keys: [{ key_id: 'a' }] }), /non-empty key_id and a pubkey/);
});

// ---------------------------------------------------------------------------
// Tier A refusal.
// ---------------------------------------------------------------------------

test('sig-ed25519/1: registering with tierCeiling overridden to A is impossible (binds.zktag + tierCeiling A refused)', () => {
  const plug = { ...edPlug(), tierCeiling: 'A' };
  assert.throws(
    () => makeVerifier({ plugs: { 'sig-ed25519/1': plug } }),
    /tier A never carries a zktag/,
  );
});

test('sig-p256/1: registering with tierCeiling overridden to A is impossible (binds.zktag + tierCeiling A refused)', () => {
  const plug = { ...ecPlug(), tierCeiling: 'A' };
  assert.throws(
    () => makeVerifier({ plugs: { 'sig-p256/1': plug } }),
    /tier A never carries a zktag/,
  );
});

test('sig-ed25519/1: routing at tier A with no zktag yields evidence_zktag_unavailable, ok:false', async () => {
  // A linkability override lets the item register (tierCeiling stays 'B',
  // which is fine for accept-at-A routing purposes here) so we can exercise
  // the zktag-unavailable path specifically, not the tier-A linkability
  // refusal that would otherwise fire first.
  const plug = { ...edPlug(), linkability: 'none' };
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': plug } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [{ type: 'sig-ed25519', version: 1, data: { key_id: 'att1', sig: 'AAAA' } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
  assert.equal(out.reason, 'evidence_zktag_unavailable');
});

test('sig-p256/1: routing at tier A with no zktag yields evidence_zktag_unavailable, ok:false', async () => {
  const plug = { ...ecPlug(), linkability: 'none' };
  const v = makeVerifier({ accept: ['sig-p256/1'], plugs: { 'sig-p256/1': plug } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [{ type: 'sig-p256', version: 1, data: { key_id: 'att1', sig: 'AAAA' } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
  assert.equal(out.reason, 'evidence_zktag_unavailable');
});

test('sig-ed25519/1: unmodified plug (linkability signer) is refused outright at tier A by evidence_forbidden_at_tier_a', async () => {
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': edPlug() } });
  const c = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [{ type: 'sig-ed25519', version: 1, data: { key_id: 'att1', sig: 'AAAA' } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'evidence_forbidden_at_tier_a');
});

// ---------------------------------------------------------------------------
// Nonce encoding: prove base64url-DECODED bytes are used, not the utf8
// nonce string — a test that would pass under either encoding is worthless.
// ---------------------------------------------------------------------------

test('sig-ed25519/1: signing over the utf8 nonce STRING (instead of base64url-decoded bytes) fails', async () => {
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': edPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  // Build the message by hand using utf8(nonce) instead of base64url-decoding
  // it, deliberately diverging from sigEd25519Message. The nonce chosen here
  // must actually differ under the two encodings, or this test is vacuous —
  // asserted below.
  const utf8Bytes = Buffer.from(c.nonce, 'utf8');
  const b64uBytes = Buffer.from(c.nonce, 'base64url');
  assert.notDeepEqual(utf8Bytes, b64uBytes, 'fixture nonce must differ under utf8 vs base64url decoding, or this test proves nothing');
  const { createHash } = await import('node:crypto');
  const { canonicalize } = await import('../../src/canonical.js');
  const claimHash = createHash('sha256').update(canonicalize(p.claim), 'utf8').digest();
  const wrongPreimage = Buffer.concat([
    Buffer.from('sig-ed25519/1\n', 'utf8'), claimHash, utf8Bytes, Buffer.from(SCOPE, 'utf8'), Buffer.from(p.zktag, 'utf8'),
  ]);
  const wrongMessage = createHash('sha256').update(wrongPreimage).digest();
  const sig = edSign(null, wrongMessage, edKeys.privateKey).toString('base64');
  p.evidence = [{ type: 'sig-ed25519', version: 1, data: { key_id: 'att1', sig } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'sig_invalid');
});

test('sig-p256/1: signing over the utf8 nonce STRING (instead of base64url-decoded bytes) fails', async () => {
  const v = makeVerifier({ accept: ['sig-p256/1'], plugs: { 'sig-p256/1': ecPlug() } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  const utf8Bytes = Buffer.from(c.nonce, 'utf8');
  const b64uBytes = Buffer.from(c.nonce, 'base64url');
  assert.notDeepEqual(utf8Bytes, b64uBytes, 'fixture nonce must differ under utf8 vs base64url decoding, or this test proves nothing');
  const { createHash } = await import('node:crypto');
  const { canonicalize } = await import('../../src/canonical.js');
  const claimHash = createHash('sha256').update(canonicalize(p.claim), 'utf8').digest();
  const wrongPreimage = Buffer.concat([
    Buffer.from('sig-p256/1\n', 'utf8'), claimHash, utf8Bytes, Buffer.from(SCOPE, 'utf8'), Buffer.from(p.zktag, 'utf8'),
  ]);
  const sig = ecSign('sha256', wrongPreimage, ecKeys.privateKey).toString('base64');
  p.evidence = [{ type: 'sig-p256', version: 1, data: { key_id: 'att1', sig } }];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'sig_invalid');
});

// ---------------------------------------------------------------------------
// A plug throwing internally must map to ok:false, never allowed:false —
// extends the existing adversarial-plug matrix (evidence.test.js) to this
// family's own verify() by forcing an internal throw path.
// ---------------------------------------------------------------------------

test('sig-ed25519/1: an internal throw inside verify() maps to ok:false, never allowed:false', async () => {
  const plug = edPlug();
  // Force the plug's own message-building step to throw by handing it a
  // ctx.claim that canonicalize() rejects (a float) — this exercises the
  // plug's outer try/catch, not a hand-rolled fixture double.
  const broken = {
    ...plug,
    verify: (item, ctx) => plug.verify(item, { ...ctx, claim: { over_threshold: true, threshold: 1.5 } }),
  };
  const v = makeVerifier({ accept: ['sig-ed25519/1'], plugs: { 'sig-ed25519/1': broken } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [edEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
});

test('sig-p256/1: an internal throw inside verify() maps to ok:false, never allowed:false', async () => {
  const plug = ecPlug();
  const broken = {
    ...plug,
    verify: (item, ctx) => plug.verify(item, { ...ctx, claim: { over_threshold: true, threshold: 1.5 } }),
  };
  const v = makeVerifier({ accept: ['sig-p256/1'], plugs: { 'sig-p256/1': broken } });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0 });
  const p = presentationFor(c);
  p.evidence = [ecEvidence(p.claim, c.nonce, p.zktag)];
  const out = await v.verify(p, { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
});

test('sig-ed25519/1: scopeDomain unconfigured (empty string) at ctx level maps to ok:false, valid:null via the plug directly', () => {
  const plug = edPlug();
  const item = { type: 'sig-ed25519', version: 1, data: { key_id: 'att1', sig: 'AAAA' } };
  const result = plug.verify(item, {
    claim: { over_threshold: true, threshold: 18 }, nonce: 'AAAA', scopeDomain: '', zktag: 'zktag-x',
  });
  assert.equal(result.ok, false);
  assert.equal(result.valid, null);
  assert.equal(result.reason, 'scope_domain_unconfigured');
});
