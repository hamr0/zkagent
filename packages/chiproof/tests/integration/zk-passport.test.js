import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { writeFileSync, chmodSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createVerifier } from '../../src/index.js';
import { zkPassport, subscopeFromNonce, scopeField, paramCommitment } from '../../src/plugs/zk-passport.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';
import { loadArtefacts, BB_PATH } from '../fixtures/zk-artefacts.js';

const BN254_MODULUS = 0x30644e72e131a029b85045b68181585d2833e84879b97091b85045b68181585dn;
const A = loadArtefacts();
const skip = A.skip ?? false;
const ZK = 'zk-passport/1';

function item(stages) { return { type: 'zk-passport', version: 1, data: { stages } }; }
function ctxFor(doc, extra = {}) {
  const d = A.docs[doc];
  return { nonce: d.challenge.nonce, claim: { over_threshold: true, threshold: 18 }, tier: 'A', scopeDomain: d.scopeDomain, now: d.now, ...extra };
}
function plug(extra = {}) { return zkPassport({ bbPath: BB_PATH, vks: A.allVks, threshold: 18, ...extra }); }

// --- pure helpers: run everywhere -------------------------------------------

test('subscopeFromNonce / scopeField: 32-byte Fields below the BN254 modulus, leading byte zero', () => {
  for (const s of ['a', randomBytes(40).toString('base64url'), 'site-a']) {
    for (const f of [subscopeFromNonce(s), scopeField(s)]) {
      assert.equal(f.length, 32);
      assert.equal(f[0], 0);
      assert.ok(BigInt('0x' + f.toString('hex')) < BN254_MODULUS);
    }
  }
  assert.notDeepEqual(subscopeFromNonce('n1'), subscopeFromNonce('n2'));
});

test('paramCommitment: pinned table covers 0..100, entries distinct, none beyond', () => {
  const seen = new Set();
  for (let t = 0; t <= 100; t += 1) {
    const c = paramCommitment(t);
    assert.equal(c.length, 32, `threshold ${t}`);
    seen.add(c.toString('hex'));
  }
  assert.equal(seen.size, 101);
  assert.equal(paramCommitment(101), undefined);
  assert.equal(paramCommitment(-1), undefined);
});

test('registration: a missing bb path or a wrong bb version refuses to register', () => {
  const dir = mkdtempSync(join(tmpdir(), 'chiproof-fakebb-'));
  try {
    const fake = join(dir, 'bb');
    writeFileSync(fake, '#!/bin/sh\necho 4.9.9\n');
    chmodSync(fake, 0o755);
    assert.throws(() => zkPassport({ bbPath: join(dir, 'nope'), vks: {}, threshold: 18 }), /cannot run bb/);
    assert.throws(() => zkPassport({ bbPath: fake, vks: {}, threshold: 18 }), /version "4\.9\.9", pinned 5\.0\.0/);
    assert.throws(() => zkPassport({ vks: {}, threshold: 18 }), /explicit bbPath/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// --- against the real artefacts ----------------------------------------------

test('registration: threshold outside the pinned table and unreadable VKs refuse', { skip }, () => {
  assert.throws(() => plug({ threshold: 150 }), /no pinned param_commitment/);
  assert.throws(() => plug({ vks: { ...A.allVks, age: '/nonexistent/vk' } }), /cannot read vk/);
});

test('real NL and US compositions verify under their own nonce (timed)', { skip }, async (t) => {
  const p = plug();
  for (const doc of Object.keys(A.docs)) {
    const start = process.hrtime.bigint();
    const r = await p.verify(item(A.docs[doc].stages), ctxFor(doc));
    const ms = Number(process.hrtime.bigint() - start) / 1e6;
    t.diagnostic(`${doc}: plug.verify (4 x bb verify + bindings) took ${ms.toFixed(1)} ms`);
    assert.deepEqual(r, { ok: true, valid: true, reason: 'zk_verified' }, doc);
  }
});

test('nonce binding: the same proof presented under a different nonce is zk_nonce_mismatch', { skip }, async () => {
  const p = plug();
  const other = randomBytes(56).toString('base64url');
  const r = await p.verify(item(A.docs.nl.stages), ctxFor('nl', { nonce: other }));
  assert.equal(r.valid, false);
  assert.equal(r.reason, 'zk_nonce_mismatch');
});

test('claim binding: a verifier configured for threshold 21 refuses the over-18 proof with zk_threshold_mismatch', { skip }, async () => {
  const r = await plug({ threshold: 21 }).verify(item(A.docs.nl.stages), ctxFor('nl'));
  assert.equal(r.valid, false);
  assert.equal(r.reason, 'zk_threshold_mismatch');
});

test('scope binding: presenting under another scopeDomain is zk_scope_mismatch', { skip }, async () => {
  const r = await plug().verify(item(A.docs.nl.stages), ctxFor('nl', { scopeDomain: 'site-b' }));
  assert.equal(r.valid, false);
  assert.equal(r.reason, 'zk_scope_mismatch');
});

test('chain binding: splicing the US integrity stage into the NL composition is zk_chain_broken', { skip: skip || !A.docs?.us }, async () => {
  const stages = { ...A.docs.nl.stages, integrity: A.docs.us.stages.integrity };
  const r = await plug().verify(item(stages), ctxFor('nl'));
  assert.equal(r.valid, false);
  assert.equal(r.reason, 'zk_chain_broken');
});

test('MATRIX 16: one bit flipped inside a proof field element (kept below the modulus) is zk_proof_invalid via bb', { skip }, async (t) => {
  const proof = Buffer.from(A.docs.nl.stages.age.proof, 'base64');
  // Flip the low bit of the low byte of the 2nd 32-byte element; assert the
  // element still parses as a canonical field so bb reaches the cryptographic
  // check rather than rejecting at deserialisation.
  const flipped = Buffer.from(proof);
  flipped[63] ^= 0x01;
  const elem = BigInt('0x' + flipped.subarray(32, 64).toString('hex'));
  assert.ok(elem < BN254_MODULUS, 'flipped element must stay below the modulus');
  const stages = { ...A.docs.nl.stages, age: { ...A.docs.nl.stages.age, proof: flipped.toString('base64') } };
  const r = await plug().verify(item(stages), ctxFor('nl'));
  t.diagnostic(`bb rejection text: ${r.detail ?? '(none captured)'}`);
  assert.equal(r.ok, true);
  assert.equal(r.valid, false);
  assert.equal(r.reason, 'zk_proof_invalid');
  assert.equal(r.stage, 'age');
});

test('a flipped public-input byte (the nonce field) is caught by the byte check before bb: zk_nonce_mismatch', { skip }, async () => {
  const pi = Buffer.from(A.docs.nl.stages.age.public_inputs, 'base64');
  pi[127] ^= 0x01;
  const stages = { ...A.docs.nl.stages, age: { ...A.docs.nl.stages.age, public_inputs: pi.toString('base64') } };
  const r = await plug().verify(item(stages), ctxFor('nl'));
  assert.equal(r.reason, 'zk_nonce_mismatch');
});

test('vk selection: with several VKs pinned for a stage, an envelope that names none is zk_unknown_circuit', { skip: skip || !A.docs?.us }, async () => {
  const { vk_sha256, ...noSelector } = A.docs.nl.stages.dsc;
  const stages = { ...A.docs.nl.stages, dsc: noSelector };
  const r = await plug().verify(item(stages), ctxFor('nl'));
  assert.equal(r.reason, 'zk_unknown_circuit');
  // The same envelope verifies when the stage has exactly one pinned VK.
  const single = plug({ vks: { ...A.allVks, dsc: A.docs.nl.vks.dsc } });
  assert.equal((await single.verify(item(stages), ctxFor('nl'))).valid, true);
});

test('malformed envelope shapes are real noes', { skip }, async () => {
  const p = plug();
  for (const data of [undefined, {}, { stages: {} }, { stages: { ...A.docs.nl.stages, age: { proof: 'AA', public_inputs: 'AA' } } }]) {
    const r = await p.verify({ type: 'zk-passport', version: 1, data }, ctxFor('nl'));
    assert.equal(r.ok, true);
    assert.equal(r.reason, 'zk_envelope_malformed');
  }
});

test('tier B is refused by the core before the plug runs (ceiling A)', { skip }, async () => {
  let calls = 0;
  const real = plug();
  const spy = { ...real, verify: (...args) => { calls += 1; return real.verify(...args); } };
  const v = createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, allowInMemoryStore: true,
    challengeSecret: randomBytes(32), scopeDomain: A.scopeDomain, threshold: 18, tiers: { max: 'C' },
    evidence: { accept: [ZK], plugs: { [ZK]: spy } },
  });
  const c = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: A.now });
  const out = await v.verify({
    spec: 'zkagent/1', tier: 'B', zktag: 'z', claim: { over_threshold: true, threshold: 18 }, challenge: c,
    evidence: [item(A.docs.nl.stages)],
  }, { now: A.now });
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'evidence_tier_exceeds_plug_ceiling');
  assert.equal(calls, 0, 'plug.verify (and therefore bb) must not have been invoked');
});

test('max_scan_age: a tiny allowance refuses the re-proved scan as zk_scan_too_old; null (unlimited) accepts it', { skip }, async () => {
  const p = plug();
  const tooOld = await p.verify(item(A.docs.nl.stages), ctxFor('nl', { maxScanAge: 1 }));
  assert.equal(tooOld.valid, false);
  assert.equal(tooOld.reason, 'zk_scan_too_old');

  const unlimited = await p.verify(item(A.docs.nl.stages), ctxFor('nl', { maxScanAge: null }));
  assert.equal(unlimited.valid, true);
  const generous = await p.verify(item(A.docs.nl.stages), ctxFor('nl', { maxScanAge: 365 * 24 * 3600 * 1000 }));
  assert.equal(generous.valid, true);
});

// ---------------------------------------------------------------------------
// F3 / F1: bb failure classification and temp-dir cleanup, with fake bb
// scripts (each prints 5.0.0 for --version so registration passes).
// ---------------------------------------------------------------------------

function fakeBb(dir, body) {
  const path = join(dir, 'bb');
  writeFileSync(path, `#!/bin/sh\nif [ "$1" = "--version" ]; then echo 5.0.0; exit 0; fi\n${body}\n`);
  chmodSync(path, 0o755);
  return path;
}

test('F3: a bb that exceeds timeoutMs (killed by signal) is ok:false zk_bb_unavailable, not a no', { skip }, async () => {
  const dir = mkdtempSync(join(tmpdir(), 'chiproof-fakebb-'));
  try {
    const p = plug({ bbPath: fakeBb(dir, 'sleep 5; exit 0'), timeoutMs: 200 });
    const r = await p.verify(item(A.docs.nl.stages), ctxFor('nl'));
    assert.equal(r.ok, false);
    assert.equal(r.valid, null);
    assert.equal(r.reason, 'zk_bb_unavailable');
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('F3: a bb that exits 1 with its own stderr is valid:false zk_proof_invalid with the detail captured', { skip }, async () => {
  const dir = mkdtempSync(join(tmpdir(), 'chiproof-fakebb-'));
  try {
    const p = plug({ bbPath: fakeBb(dir, 'echo "Proof verification failed" >&2; exit 1') });
    const r = await p.verify(item(A.docs.nl.stages), ctxFor('nl'));
    assert.equal(r.ok, true);
    assert.equal(r.valid, false);
    assert.equal(r.reason, 'zk_proof_invalid');
    assert.equal(r.stage, 'dsc', 'first failing stage in STAGES order, whatever settled first');
    assert.match(r.detail, /verification failed/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('F3: registration rejects a non-positive timeoutMs', () => {
  assert.throws(() => zkPassport({ bbPath: BB_PATH, vks: {}, threshold: 18, timeoutMs: 0 }), /timeoutMs/);
});

test('F1: when the temp dir cannot be removed the verdict stands and warnings:[tmpdir_cleanup_failed] is set (deterministic: fake bb makes the parent read-only)', { skip: skip || process.getuid?.() === 0 }, async () => {
  const base = mkdtempSync(join(tmpdir(), 'chiproof-tmpbase-'));
  const fake = mkdtempSync(join(tmpdir(), 'chiproof-fakebb-'));
  try {
    // While "verifying", the fake bb strips write permission from the base
    // dir, so unlinking the plug's mkdtemp child inside it must fail (EACCES).
    const p = plug({ bbPath: fakeBb(fake, `chmod 555 "${base}"; exit 0`), tmpDir: base });
    const r = await p.verify(item(A.docs.nl.stages), ctxFor('nl'));
    assert.equal(r.ok, true);
    assert.equal(r.valid, true);
    assert.deepEqual(r.warnings, ['tmpdir_cleanup_failed']);
  } finally {
    chmodSync(base, 0o755);
    rmSync(base, { recursive: true, force: true });
    rmSync(fake, { recursive: true, force: true });
  }
});
