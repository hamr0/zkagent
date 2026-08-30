import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes, generateKeyPairSync } from 'node:crypto';
import { createVerifier } from '../../src/index.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';

// All key material is generated at runtime -- nothing in the tree, no PII.
const SECRET = randomBytes(32);
const T0 = 1_800_000_000_000;

const issuerB = generateKeyPairSync('ed25519'); // pinned with ceiling B
const issuerC = generateKeyPairSync('ed25519'); // pinned with ceiling C
const TRUSTED_ISSUERS = [
  { pubkey: issuerB.publicKey, key_id: 'issuer-b', maxTier: 'B' },
  { pubkey: issuerC.publicKey, key_id: 'issuer-c', maxTier: 'C' },
];

const CLIENT = { name: 'zkagent-app', package: 'org.zkagent.app', certDigest: 'sha256:abc', specVersion: '1' };
const TRUSTED_CLIENTS = [CLIENT];

function makeVerifier(overrides = {}) {
  return createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
    allowInMemoryStore: true,
    challengeSecret: SECRET,
    threshold: 18,
    tiers: { max: 'C' },
    trustedChallengeIssuers: TRUSTED_ISSUERS,
    trustedClients: TRUSTED_CLIENTS,
    ...overrides,
  });
}

/** A presentation that matches its challenge on every axis; tweak from here. */
function presentationFor(challenge, extra = {}) {
  const p = {
    spec: 'zkagent/1',
    tier: challenge.tier,
    claim: { over_threshold: true, threshold: challenge.threshold },
    challenge,
    evidence: [],
  };
  if (challenge.tier !== 'A') p.zktag = 'zktag-' + randomBytes(8).toString('hex');
  return { ...p, ...extra };
}

function challengeOpts(tier, extra = {}) {
  return { tier, threshold: 18, ttlMs: 60_000, now: T0, ...extra };
}

/** Assert the §3 invariant on any verdict, whatever else the test checks. */
function assertInvariant(v) {
  assert.equal(typeof v.ok, 'boolean');
  if (v.ok === false) assert.equal(v.allowed, null, 'ok:false must force allowed:null');
  else assert.equal(typeof v.allowed, 'boolean');
  assert.equal(typeof v.reason, 'string');
}

// ---------------------------------------------------------------------------
// Happy paths first: the harness must be able to say yes before any no counts.
// ---------------------------------------------------------------------------

test('happy: unsigned tier A presentation is allowed, verdict carries tier and no zktag', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  const out = await v.verify(presentationFor(c), { now: T0 });
  assertInvariant(out);
  assert.deepEqual(out, { ok: true, allowed: true, reason: 'allowed', tier: 'A' });
});

test('happy: signed tier C presentation with a zktag is allowed and echoes the zktag', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('C', { issuer: { privateKey: issuerC.privateKey, key_id: 'issuer-c' } }));
  const p = presentationFor(c);
  const out = await v.verify(p, { now: T0 });
  assert.deepEqual(out, { ok: true, allowed: true, reason: 'allowed', tier: 'C', zktag: p.zktag });
});

// ---------------------------------------------------------------------------
// Matrix 6: tier presented > tier requested.
// ---------------------------------------------------------------------------

test('MATRIX 6: presenting tier B against a tier-A challenge is refused; presenting A passes', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('A'));
  const bad = await v.verify(presentationFor(c1, { tier: 'B', zktag: 'z' }), { now: T0 });
  assertInvariant(bad);
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'tier_exceeds_requested');

  const c2 = v.issueChallenge(challengeOpts('A'));
  const good = await v.verify(presentationFor(c2), { now: T0 });
  assert.equal(good.allowed, true);
});

test('tier is never downgraded either: presenting A against a tier-B challenge is refused', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('B'));
  const out = await v.verify(presentationFor(c, { tier: 'A', zktag: undefined }), { now: T0 });
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'tier_below_requested');
});

test('presented tier above config.tiers.max is refused; same presentation passes when max allows it', async () => {
  const strict = makeVerifier({ tiers: { max: 'A' } });
  const c1 = strict.issueChallenge(challengeOpts('B'));
  const bad = await strict.verify(presentationFor(c1), { now: T0 });
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'tier_exceeds_max');

  const loose = makeVerifier({ tiers: { max: 'B' } });
  const c2 = loose.issueChallenge(challengeOpts('B'));
  const good = await loose.verify(presentationFor(c2), { now: T0 });
  assert.equal(good.allowed, true);
});

// ---------------------------------------------------------------------------
// Matrix 7: tier C presented with issuer ceiling B -> refused (end-to-end).
// ---------------------------------------------------------------------------

test('MATRIX 7: tier C signed by an issuer pinned at ceiling B is refused; the ceiling-C issuer passes', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('C', { issuer: { privateKey: issuerB.privateKey, key_id: 'issuer-b' } }));
  const bad = await v.verify(presentationFor(c1), { now: T0 });
  assertInvariant(bad);
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'tier_exceeds_issuer_ceiling');

  const c2 = v.issueChallenge(challengeOpts('C', { issuer: { privateKey: issuerC.privateKey, key_id: 'issuer-c' } }));
  const good = await v.verify(presentationFor(c2), { now: T0 });
  assert.equal(good.allowed, true);
});

// ---------------------------------------------------------------------------
// Matrix 8: threshold mismatch (proof for 21 when 18 asked).
// ---------------------------------------------------------------------------

test('MATRIX 8: a claim for threshold 21 against an 18 challenge is refused; a claim for 18 passes', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('A'));
  const bad = await v.verify(presentationFor(c1, { claim: { over_threshold: true, threshold: 21 } }), { now: T0 });
  assertInvariant(bad);
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'threshold_mismatch');

  const c2 = v.issueChallenge(challengeOpts('A'));
  const good = await v.verify(presentationFor(c2, { claim: { over_threshold: true, threshold: 18 } }), { now: T0 });
  assert.equal(good.allowed, true);
});

test('MATRIX 8b: a challenge minted for threshold 0 is refused even with a matching claim -- config.threshold caps a leaked secret', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A', { threshold: 0 }));
  const out = await v.verify(presentationFor(c, { claim: { over_threshold: true, threshold: 0 } }), { now: T0 });
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'threshold_mismatch');
});

test('over_threshold:false is an honest real no', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  const out = await v.verify(presentationFor(c, { claim: { over_threshold: false, threshold: 18 } }), { now: T0 });
  assert.equal(out.ok, true);
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'under_threshold');
});

// ---------------------------------------------------------------------------
// Matrix 9: zktag present in tier A -> reject.
// ---------------------------------------------------------------------------

test('MATRIX 9: a zktag at tier A is refused; the same presentation without one passes', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('A'));
  const bad = await v.verify(presentationFor(c1, { zktag: 'z' }), { now: T0 });
  assertInvariant(bad);
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'zktag_forbidden_at_tier_a');

  const c2 = v.issueChallenge(challengeOpts('A'));
  const good = await v.verify(presentationFor(c2), { now: T0 });
  assert.equal(good.allowed, true);
});

test('zktag is required at tier B (missing, empty, or non-string all refuse); a string passes', async () => {
  const v = makeVerifier();
  for (const zktag of [undefined, '', 42, null]) {
    const c = v.issueChallenge(challengeOpts('B'));
    const out = await v.verify(presentationFor(c, { zktag }), { now: T0 });
    assert.equal(out.allowed, false, `zktag=${String(zktag)}`);
    assert.equal(out.reason, 'zktag_required');
  }
  const c = v.issueChallenge(challengeOpts('B'));
  const good = await v.verify(presentationFor(c, { zktag: 'abc' }), { now: T0 });
  assert.equal(good.allowed, true);
  assert.equal(good.zktag, 'abc');
});

test('chip_auth (D21): forbidden at tier A, optional object at tier B, non-object refused', async () => {
  const v = makeVerifier();
  const cA = v.issueChallenge(challengeOpts('A'));
  const atA = await v.verify(presentationFor(cA, { chip_auth: {} }), { now: T0 });
  assert.equal(atA.reason, 'chip_auth_forbidden_at_tier_a');

  const cB1 = v.issueChallenge(challengeOpts('B'));
  const atB = await v.verify(presentationFor(cB1, { chip_auth: { ok: true } }), { now: T0 });
  assert.equal(atB.allowed, true);
  assert.equal('chip_auth' in atB, false, 'verdict echoes nothing about chip_auth yet');

  const cB2 = v.issueChallenge(challengeOpts('B'));
  const badB = await v.verify(presentationFor(cB2, { chip_auth: 'yes' }), { now: T0 });
  assert.equal(badB.reason, 'chip_auth_malformed');
});

// ---------------------------------------------------------------------------
// Matrix 10: client not on trust list.
// ---------------------------------------------------------------------------

test('MATRIX 10: a ctx.clientIdentity not on trustedClients is refused; a listed one passes', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('A'));
  const bad = await v.verify(presentationFor(c1), {
    now: T0, clientIdentity: { ...CLIENT, certDigest: 'sha256:other' },
  });
  assertInvariant(bad);
  assert.equal(bad.allowed, false);
  assert.equal(bad.reason, 'client_untrusted');

  const c2 = v.issueChallenge(challengeOpts('A'));
  const good = await v.verify(presentationFor(c2), { now: T0, clientIdentity: { ...CLIENT } });
  assert.equal(good.allowed, true);
});

test('trust list: specVersion must match when both sides state one, and is ignored when either omits it', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('A'));
  const mismatch = await v.verify(presentationFor(c1), { now: T0, clientIdentity: { ...CLIENT, specVersion: '2' } });
  assert.equal(mismatch.reason, 'client_untrusted');

  const c2 = v.issueChallenge(challengeOpts('A'));
  const { specVersion, ...noVersion } = CLIENT;
  const lenient = await v.verify(presentationFor(c2), { now: T0, clientIdentity: noVersion });
  assert.equal(lenient.allowed, true);
});

test('trust list: skipped when no clientIdentity is supplied (bare mode) or trustedClients is empty', async () => {
  const v = makeVerifier();
  const c1 = v.issueChallenge(challengeOpts('A'));
  assert.equal((await v.verify(presentationFor(c1), { now: T0 })).allowed, true);

  const open = makeVerifier({ trustedClients: [] });
  const c2 = open.issueChallenge(challengeOpts('A'));
  const out = await open.verify(presentationFor(c2), { now: T0, clientIdentity: { package: 'x', certDigest: 'y' } });
  assert.equal(out.allowed, true);
});

test('a presentation carrying a self-declared client field is NOT what the trust list reads', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  // Self-claim says "trusted"; ctx says otherwise. ctx wins.
  const out = await v.verify(presentationFor(c, { client: CLIENT }), {
    now: T0, clientIdentity: { package: 'evil', certDigest: 'sha256:nope' },
  });
  assert.equal(out.reason, 'client_untrusted');
});

// ---------------------------------------------------------------------------
// Plus: spec, malformed input, replay, store failure, never throws.
// ---------------------------------------------------------------------------

test('presentation.spec !== "zkagent/1" is a real no with reason unsupported_spec', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  for (const spec of ['zkagent/2', 'zkagent', undefined, 1]) {
    const out = await v.verify(presentationFor(c, { spec }), { now: T0 });
    assert.equal(out.ok, true);
    assert.equal(out.allowed, false);
    assert.equal(out.reason, 'unsupported_spec');
  }
});

test('a challenge is single-use through verify(): the second presentation of the same challenge is refused even when the first was refused', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  const first = await v.verify(presentationFor(c, { claim: { over_threshold: false, threshold: 18 } }), { now: T0 });
  assert.equal(first.reason, 'under_threshold');
  const second = await v.verify(presentationFor(c), { now: T0 });
  assert.equal(second.allowed, false);
  assert.equal(second.reason, 'nonce_replayed');
});

test('an expired challenge is refused BEFORE the nonce is spent (a late retry with a live one still works)', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A', { ttlMs: 1000 }));
  const late = await v.verify(presentationFor(c), { now: T0 + 5000 });
  assert.equal(late.reason, 'challenge_expired');
});

test('a nonce store that throws yields ok:false / allowed:null, never a no', async () => {
  const v = makeVerifier({ stores: { nonce: { async setIfAbsent() { throw new Error('ECONNREFUSED'); } } } });
  const c = v.issueChallenge(challengeOpts('A'));
  const out = await v.verify(presentationFor(c), { now: T0 });
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
  assert.equal(out.reason, 'nonce_store_unreachable');
});

test('a forged nonce is refused without ever reaching the store', async () => {
  const calls = [];
  const v = makeVerifier({ stores: { nonce: { async setIfAbsent(k) { calls.push(k); return true; } } } });
  const c = v.issueChallenge(challengeOpts('A'));
  const forged = { ...c, nonce: 'AAAA' + c.nonce.slice(4) };
  const out = await v.verify(presentationFor(forged), { now: T0 });
  assert.equal(out.reason, 'nonce_forged');
  assert.equal(calls.length, 0);
});

test('malformed shapes are real noes with a reason -- and verify() never throws', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  const cases = [
    [null, 'presentation_malformed'],
    [undefined, 'presentation_malformed'],
    ['x', 'presentation_malformed'],
    [[], 'presentation_malformed'],
    [presentationFor(c, { tier: 'Z' }), 'tier_invalid'],
    [presentationFor(c, { challenge: 'nope' }), 'presentation_malformed'],
    [presentationFor(c, { claim: { over_threshold: 'yes', threshold: 18 } }), 'claim_malformed'],
    [presentationFor(c, { claim: { over_threshold: true, threshold: 18.5 } }), 'claim_malformed'],
    [presentationFor(c, { claim: { over_threshold: true, threshold: 18, extra: 1 } }), 'claim_malformed'],
    [presentationFor(c, { claim: null }), 'claim_malformed'],
    [presentationFor(c, { evidence: 'not-an-array' }), 'evidence_malformed'],
    [presentationFor(c, { challenge: { ...c, threshold: '18' } }), 'challenge_malformed'],
  ];
  for (const [input, reason] of cases) {
    let out;
    await assert.doesNotReject(async () => { out = await v.verify(input, { now: T0 }); });
    assertInvariant(out);
    assert.equal(out.ok, true, JSON.stringify(input));
    assert.equal(out.allowed, false);
    assert.equal(out.reason, reason, JSON.stringify(input));
  }
});

test('an unusable clock is ok:false, not a no', async () => {
  const v = makeVerifier();
  const c = v.issueChallenge(challengeOpts('A'));
  const out = await v.verify(presentationFor(c), { now: NaN });
  assert.equal(out.ok, false);
  assert.equal(out.allowed, null);
  assert.equal(out.reason, 'clock_unavailable');
});

test('config validation: bad threshold / tiers.max / list shapes refuse to boot', () => {
  assert.throws(() => makeVerifier({ threshold: 18.5 }), TypeError);
  assert.throws(() => makeVerifier({ tiers: { max: 'Z' } }), TypeError);
  assert.throws(() => makeVerifier({ trustedClients: 'nope' }), TypeError);
  assert.throws(() => makeVerifier({ trustedChallengeIssuers: {} }), TypeError);
});
