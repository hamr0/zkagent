import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createVerifier } from '../../src/index.js';
import { verifyChallenge } from '../../src/challenge.js';
import { InMemoryNonceStore } from '../../src/stores/memory.js';

// Generated at runtime -- no key material in the tree.
const SECRET = randomBytes(32);
const T0 = 1_800_000_000_000;

/**
 * Run `fn` with NODE_ENV explicitly set (or unset when `value` is undefined),
 * restoring the original afterwards -- success or failure -- so the boot check
 * is deterministic whatever environment `node --test` runs in.
 */
function withNodeEnv(value, fn) {
  const original = process.env.NODE_ENV;
  if (value === undefined) delete process.env.NODE_ENV;
  else process.env.NODE_ENV = value;
  try {
    return fn();
  } finally {
    if (original === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = original;
  }
}

function realShapedStore() {
  return { async setIfAbsent() { return true; } };
}

// (a) InMemoryNonceStore, NODE_ENV not 'test', no override -> throws.
test('boot (a): InMemoryNonceStore is refused when NODE_ENV is unset and no override is given', () => {
  withNodeEnv(undefined, () => {
    assert.throws(
      () => createVerifier({ stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, challengeSecret: SECRET }),
      TypeError,
    );
  });
});

test('boot (a): InMemoryNonceStore is refused under NODE_ENV=production without an override', () => {
  withNodeEnv('production', () => {
    assert.throws(
      () => createVerifier({ stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, challengeSecret: SECRET }),
      TypeError,
    );
  });
});

// Non-vacuity for (a): the same store IS accepted under NODE_ENV=test, so the
// refusal above is the env gate working, not "InMemoryNonceStore always throws".
test('boot (a, control): InMemoryNonceStore boots under NODE_ENV=test with no override', () => {
  withNodeEnv('test', () => {
    assert.doesNotThrow(
      () => createVerifier({ stores: { nonce: new InMemoryNonceStore({ quiet: true }) }, challengeSecret: SECRET }),
    );
  });
});

// (b) allowInMemoryStore:true -> boots, and its issueChallenge() output is
// accepted by verifyChallenge with the same secret.
test('boot (b): allowInMemoryStore:true boots outside tests and issueChallenge() mints a verifiable challenge', () => {
  withNodeEnv('production', () => {
    let verifier;
    assert.doesNotThrow(() => {
      verifier = createVerifier({
        stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
        challengeSecret: SECRET,
        allowInMemoryStore: true,
      });
    });
    assert.equal(typeof verifier.issueChallenge, 'function');

    const challenge = verifier.issueChallenge({ tier: 'A', threshold: 18, ttlMs: 60_000, now: T0 });
    assert.equal(challenge.tier, 'A');
    assert.equal(challenge.threshold, 18);
    assert.equal(challenge.issued_at, T0);

    const sameSecret = verifyChallenge(challenge, { now: T0, challengeSecret: SECRET });
    assert.equal(sameSecret.ok, true);
    assert.equal(sameSecret.valid, true, 'the verifier must recognise the nonce it minted');

    // The test can fail: a different secret must NOT recognise the nonce.
    const otherSecret = verifyChallenge(challenge, { now: T0, challengeSecret: randomBytes(32) });
    assert.equal(otherSecret.valid, false);
    assert.equal(otherSecret.reason, 'nonce_forged');
  });
});

// (c) missing stores.nonce -> throws.
test('boot (c): a missing stores.nonce is refused, not silently allowed through', () => {
  withNodeEnv('test', () => {
    assert.throws(() => createVerifier({ challengeSecret: SECRET }), TypeError);
    assert.throws(() => createVerifier({ stores: {}, challengeSecret: SECRET }), TypeError);
  });
});

// (d) store without a setIfAbsent function -> throws.
test('boot (d): a store without a setIfAbsent function is refused', () => {
  withNodeEnv('test', () => {
    assert.throws(() => createVerifier({ stores: { nonce: {} }, challengeSecret: SECRET }), TypeError);
    assert.throws(
      () => createVerifier({ stores: { nonce: { setIfAbsent: 'not-a-function' } }, challengeSecret: SECRET }),
      TypeError,
    );
  });
});

// (e) missing config.challengeSecret -> throws, even with a valid real-shaped store.
test('boot (e): a missing config.challengeSecret is refused even with a valid real-shaped store', () => {
  withNodeEnv('production', () => {
    assert.throws(() => createVerifier({ stores: { nonce: realShapedStore() } }), TypeError);
    // Control: the same store with a secret boots in any NODE_ENV, no override needed.
    assert.doesNotThrow(() => createVerifier({ stores: { nonce: realShapedStore() }, challengeSecret: SECRET }));
  });
});

test('boot: a non-object config is refused', () => {
  for (const bad of [null, undefined, 'x', 42]) {
    assert.throws(() => createVerifier(bad), TypeError);
  }
});
