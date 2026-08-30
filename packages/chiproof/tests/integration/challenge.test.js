import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { issueChallenge, verifyChallenge, spendNonce } from '../../src/challenge.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';

const SECRET = 'a'.repeat(32);

function issuerKeyPair() {
  return generateKeyPairSync('ed25519');
}

// --- Matrix 1: replayed nonce -----------------------------------------------

test('matrix 1: first spend is fresh, replay of the same nonce is refused', async () => {
  const challenge = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });
  const store = new InMemoryNonceStore({ quiet: true });

  const first = await spendNonce(challenge, store, { now: 1000 });
  assert.deepEqual(first, { ok: true, valid: true, reason: 'nonce_fresh' });

  const second = await spendNonce(challenge, store, { now: 1000 });
  assert.deepEqual(second, { ok: true, valid: false, reason: 'nonce_replayed' });
});

// --- Matrix 2: expired challenge ---------------------------------------------

test('matrix 2: challenge expired at now, valid one ms earlier', () => {
  const challenge = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 500, challengeSecret: SECRET, now: 1000 });
  assert.equal(challenge.expires_at, 1500);

  const expired = verifyChallenge(challenge, { now: 1501, challengeSecret: SECRET });
  assert.equal(expired.ok, true);
  assert.equal(expired.valid, false);
  assert.equal(expired.reason, 'challenge_expired');

  const stillValid = verifyChallenge(challenge, { now: 1500, challengeSecret: SECRET });
  assert.equal(stillValid.valid, true);
});

// --- Matrix 3: tier C signature against trusted issuer list ------------------

test('matrix 3: tier C signed by an unlisted key is refused; listed key with maxTier C is valid', () => {
  const { privateKey, publicKey } = issuerKeyPair();
  const challenge = issueChallenge({
    tier: 'C', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000,
    issuer: { privateKey, key_id: 'issuer-1' },
  });

  const unknown = verifyChallenge(challenge, { now: 1000, challengeSecret: SECRET, trustedChallengeIssuers: [] });
  assert.equal(unknown.valid, false);
  assert.equal(unknown.reason, 'signature_unknown_issuer');

  const known = verifyChallenge(challenge, {
    now: 1000,
    challengeSecret: SECRET,
    trustedChallengeIssuers: [{ pubkey: publicKey, key_id: 'issuer-1', maxTier: 'C' }],
  });
  assert.equal(known.valid, true);
  assert.equal(known.reason, 'challenge_valid');
});

// --- Matrix 4: unsigned challenge at tier C vs tier B ------------------------

test('matrix 4: unsigned tier C is refused, unsigned tier B is valid', () => {
  const tierC = issueChallenge({ tier: 'C', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });
  const cResult = verifyChallenge(tierC, { now: 1000, challengeSecret: SECRET });
  assert.equal(cResult.valid, false);
  assert.equal(cResult.reason, 'signature_required_at_tier_c');

  const tierB = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });
  const bResult = verifyChallenge(tierB, { now: 1000, challengeSecret: SECRET });
  assert.equal(bResult.valid, true);
});

// --- Matrix 5: store failure modes -------------------------------------------

test('matrix 5: an unreachable store maps to ok:false, a working store maps to ok:true', async () => {
  const challenge = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });

  const brokenStore = { setIfAbsent: async () => { throw new Error('unreachable'); } };
  const brokenResult = await spendNonce(challenge, brokenStore, { now: 1000 });
  assert.deepEqual(brokenResult, { ok: false, valid: null, reason: 'nonce_store_unreachable' });

  const rejectingStore = { setIfAbsent: () => Promise.reject(new Error('down')) };
  const rejectingResult = await spendNonce(challenge, rejectingStore, { now: 1000 });
  assert.deepEqual(rejectingResult, { ok: false, valid: null, reason: 'nonce_store_unreachable' });

  const workingStore = new InMemoryNonceStore({ quiet: true });
  const workingResult = await spendNonce(challenge, workingStore, { now: 1000 });
  assert.equal(workingResult.ok, true);
});

test('matrix 5b: a missing store is a config-shape problem, not a store failure -- ok:false, nonce_store_misconfigured', async () => {
  const challenge = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });
  const result = await spendNonce(challenge, undefined, { now: 1000 });
  assert.deepEqual(result, { ok: false, valid: null, reason: 'nonce_store_misconfigured' });
});

// --- Extra: forged / tampered nonce ------------------------------------------

test('extra: a nonce that never came from mintNonce is refused as forged', () => {
  const challenge = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });
  const forged = { ...challenge, nonce: Buffer.alloc(56, 7).toString('base64url') };
  const result = verifyChallenge(forged, { now: 1000, challengeSecret: SECRET });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'nonce_forged');
});

test('extra: editing issued_at after minting is caught as a forged nonce (mismatched encoding)', () => {
  const challenge = issueChallenge({ tier: 'B', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000 });
  const tampered = { ...challenge, issued_at: challenge.issued_at + 1 };
  const result = verifyChallenge(tampered, { now: 1000, challengeSecret: SECRET });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'nonce_forged');
});

test('extra: a tier exceeding the issuer\'s ceiling is refused even with a valid signature', () => {
  const { privateKey, publicKey } = issuerKeyPair();
  const challenge = issueChallenge({
    tier: 'C', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000,
    issuer: { privateKey, key_id: 'issuer-1' },
  });
  const result = verifyChallenge(challenge, {
    now: 1000,
    challengeSecret: SECRET,
    trustedChallengeIssuers: [{ pubkey: publicKey, key_id: 'issuer-1', maxTier: 'B' }],
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'tier_exceeds_issuer_ceiling');
});

test('extra: a signed challenge tampered with after signing fails signature verification', () => {
  const { privateKey, publicKey } = issuerKeyPair();
  const challenge = issueChallenge({
    tier: 'C', threshold: 18, ttlMs: 60_000, challengeSecret: SECRET, now: 1000,
    issuer: { privateKey, key_id: 'issuer-1' },
  });
  const tampered = { ...challenge, threshold: 21 };
  const result = verifyChallenge(tampered, {
    now: 1000,
    challengeSecret: SECRET,
    trustedChallengeIssuers: [{ pubkey: publicKey, key_id: 'issuer-1', maxTier: 'C' }],
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'signature_invalid');
});

test('extra: every malformed input to verifyChallenge/spendNonce is a real no, never a throw', async () => {
  const malformed = [null, 'not-an-object', {}, []];
  for (const bad of malformed) {
    assert.doesNotThrow(() => verifyChallenge(bad, { now: 1000, challengeSecret: SECRET }));
    const vResult = verifyChallenge(bad, { now: 1000, challengeSecret: SECRET });
    assert.equal(vResult.ok, true);
    assert.equal(vResult.valid, false);

    await assert.doesNotReject(spendNonce(bad, new InMemoryNonceStore({ quiet: true }), { now: 1000 }));
    const sResult = await spendNonce(bad, new InMemoryNonceStore({ quiet: true }), { now: 1000 });
    assert.equal(sResult.ok, true);
    assert.equal(sResult.valid, false);
  }
});
