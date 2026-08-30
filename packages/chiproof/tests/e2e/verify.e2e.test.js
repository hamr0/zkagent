import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createVerifier } from '../../src/index.js';
import { zkPassport } from '../../src/plugs/zk-passport.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';
import { loadArtefacts, BB_PATH } from '../fixtures/zk-artefacts.js';

const A = loadArtefacts();
const skip = A.skip ?? false;
const ZK = 'zk-passport/1';

test('e2e bare mode: tier B, no evidence configured -> allowed, no-evidence-required, evidence []', async () => {
  const v = createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, allowInMemoryStore: true,
    challengeSecret: randomBytes(32), scopeDomain: 'example.test', threshold: 18, tiers: { max: 'B' },
  });
  const now = Date.now();
  const challenge = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now });
  const out = await v.verify({
    spec: 'zkagent/1', tier: 'B', zktag: 'zktag-' + randomBytes(8).toString('hex'),
    claim: { over_threshold: true, threshold: 18 }, challenge,
  }, { now });
  assert.equal(out.ok, true);
  assert.equal(out.allowed, true);
  assert.equal(out.reason, 'no-evidence-required');
  assert.deepEqual(out.evidence, []);
  assert.equal(out.tier, 'B');
});

test('e2e zk-passport: real NL and US compositions, tier A, through createVerifier().verify (timed)', { skip }, async (t) => {
  // The challenges were minted at re-prove time under A.challengeSecret; the
  // verifier is built with that same secret so verifyChallenge recognises them.
  const v = createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, allowInMemoryStore: true,
    challengeSecret: A.challengeSecret, scopeDomain: A.scopeDomain, threshold: 18, tiers: { max: 'A' },
    evidence: { require: [ZK], plugs: { [ZK]: zkPassport({ bbPath: BB_PATH, vks: A.allVks, threshold: 18 }) } },
  });
  for (const [doc, d] of Object.entries(A.docs)) {
    const presentation = {
      spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, challenge: d.challenge,
      evidence: [{ type: 'zk-passport', version: 1, data: { stages: d.stages } }],
    };
    const start = process.hrtime.bigint();
    const out = await v.verify(presentation, { now: d.now });
    t.diagnostic(`${doc}: end-to-end verify took ${(Number(process.hrtime.bigint() - start) / 1e6).toFixed(1)} ms`);
    assert.deepEqual(out, { ok: true, allowed: true, reason: 'evidence-verified', tier: 'A', evidence: [ZK] }, doc);

    // Single use holds end to end: the same presentation again is a replay.
    const again = await v.verify(presentation, { now: d.now });
    assert.equal(again.reason, 'nonce_replayed');
  }
});

test('e2e zk-passport: required evidence absent is refused even with a valid challenge', { skip }, async () => {
  const v = createVerifier({
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, allowInMemoryStore: true,
    challengeSecret: randomBytes(32), scopeDomain: A.scopeDomain, threshold: 18,
    evidence: { require: [ZK], plugs: { [ZK]: zkPassport({ bbPath: BB_PATH, vks: A.allVks, threshold: 18 }) } },
  });
  const now = Date.now();
  const challenge = v.issueChallenge({ tier: 'A', ttlMs: 60_000, now });
  const out = await v.verify({ spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, challenge }, { now });
  assert.equal(out.allowed, false);
  assert.equal(out.reason, 'evidence_required_missing');
});
