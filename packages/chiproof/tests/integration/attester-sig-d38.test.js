// SPDX-License-Identifier: Apache-2.0
// D38 (2026-09-01): per-origin mode-B device keys. A `sig-ed25519/1` /
// `sig-p256/1` item may now carry `data.pubkey` (SubjectPublicKeyInfo DER,
// base64); an unpinned `key_id` is checked against an adopter-supplied
// `attesterStore` (trust-on-first-sight, bound to `(scope, zktag)`, D37/D38).
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  randomBytes, generateKeyPairSync, sign as ecSign, sign as edSign,
} from 'node:crypto';
import {
  sigEd25519, sigEd25519Message, sigP256, sigP256Message, keyIdFor,
} from '../../src/plugs/attester-sig.js';
import { InMemoryAttesterStore } from '../../src/stores/attester.js';

const SCOPE = 'example.test';
const CLAIM = { over_threshold: true, threshold: 18 };
const NONCE = 'AAECAwQFBgcICQoLDA0ODw';

const ecKeys = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const ecOther = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const edKeys = generateKeyPairSync('ed25519');

/** SubjectPublicKeyInfo DER, base64 -- exactly what a device would carry. */
function spkiB64(publicKey) {
  return publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
}

function ecItem(zktag, { privateKey = ecKeys.privateKey, publicKey = ecKeys.publicKey, keyId, includePubkey = true } = {}) {
  const der = publicKey.export({ type: 'spki', format: 'der' });
  const id = keyId ?? keyIdFor(der);
  const sig = ecSign('sha256', sigP256Message(CLAIM, NONCE, SCOPE, zktag), privateKey).toString('base64');
  const data = { key_id: id, sig };
  if (includePubkey) data.pubkey = der.toString('base64');
  return { type: 'sig-p256', version: 1, data };
}

function edItem(zktag, { privateKey = edKeys.privateKey, publicKey = edKeys.publicKey, keyId, includePubkey = true } = {}) {
  const der = publicKey.export({ type: 'spki', format: 'der' });
  const id = keyId ?? keyIdFor(der);
  const sig = edSign(null, sigEd25519Message(CLAIM, NONCE, SCOPE, zktag), privateKey).toString('base64');
  const data = { key_id: id, sig };
  if (includePubkey) data.pubkey = der.toString('base64');
  return { type: 'sig-ed25519', version: 1, data };
}

function ctxFor(zktag) {
  return {
    claim: CLAIM, nonce: NONCE, scopeDomain: SCOPE, zktag, tier: 'B', now: 1_800_000_000_000, trustedClients: [], maxScanAge: null,
  };
}

function assertInvariant(v) {
  if (v.ok === false) assert.equal(v.valid, null, 'ok:false must force valid:null');
}

// ---------------------------------------------------------------------------
// Pinned path: unchanged 0.4.0 behaviour, with and without a store configured.
// ---------------------------------------------------------------------------

test("D38: pinned key path is unaffected by an attesterStore's presence -- item without pubkey still verifies", async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigP256({ keys: [{ key_id: 'pinned-1', pubkey: ecKeys.publicKey }], attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  const item = ecItem(zktag, { keyId: 'pinned-1', includePubkey: false });
  const out = await plug.verify(item, ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.valid, true);
  assert.equal(out.reason, 'sig_verified');
  assert.equal('warnings' in out, false, 'pinned path never carries the first-sight note');
});

test('D38: a pinned key_id is resolved from the pinned list, never consulting (or fighting) the store', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  // The pinned key_id happens to be its own keyIdFor hash -- realistic (an
  // operator can pin a known device's key under the SAME id a real device
  // would generate), and keeps the carried pubkey/key_id self-consistent so
  // this test isolates ONE thing: pinned-list precedence over the store.
  const pinnedId = keyIdFor(ecKeys.publicKey.export({ type: 'spki', format: 'der' }));
  const plug = sigP256({ keys: [{ key_id: pinnedId, pubkey: ecKeys.publicKey }], attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  // The store already holds a binding for this exact (scope, zktag) to a
  // COMPLETELY DIFFERENT key -- if the plug consulted the store at all here,
  // this would be attester_key_mismatch. It must not: pinned wins outright.
  await store.bind({
    scope: SCOPE, zktag, key_id: 'someone-else', pubkey: ecOther.publicKey.export({ type: 'spki', format: 'der' }),
  });
  const item = ecItem(zktag, { keyId: pinnedId, includePubkey: true });
  const out = await plug.verify(item, ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.valid, true, 'pinned lookup short-circuits before the store is ever consulted');
});

// ---------------------------------------------------------------------------
// First sight: bind, then a later presentation with the SAME key matches.
// ---------------------------------------------------------------------------

test('D38: first-sight bind then match -- P-256, no pinned keys at all', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigP256({ attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;

  const first = await plug.verify(ecItem(zktag), ctxFor(zktag));
  assertInvariant(first);
  assert.equal(first.ok, true);
  assert.equal(first.valid, true);
  assert.equal(first.reason, 'sig_verified');
  assert.deepEqual(first.warnings, ['attester_bound_first_sight']);

  // Same (scope, zktag), same key, a fresh presentation -- the binding now matches.
  const second = await plug.verify(ecItem(zktag), ctxFor(zktag));
  assertInvariant(second);
  assert.equal(second.ok, true);
  assert.equal(second.valid, true);
  assert.equal(second.reason, 'sig_verified');
  assert.equal('warnings' in second, false, 'the SECOND sighting is not first sight -- no note');
});

test('D38: first-sight bind then match -- sig-ed25519/1, no pinned keys at all', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigEd25519({ attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;

  const first = await plug.verify(edItem(zktag), ctxFor(zktag));
  assert.equal(first.valid, true);
  assert.deepEqual(first.warnings, ['attester_bound_first_sight']);

  const second = await plug.verify(edItem(zktag), ctxFor(zktag));
  assert.equal(second.valid, true);
  assert.equal('warnings' in second, false);
});

// ---------------------------------------------------------------------------
// First sight, then a DIFFERENT key for the same (scope, zktag).
// ---------------------------------------------------------------------------

test('D38: first-sight bind, then a different key for the SAME (scope, zktag) -> attester_key_mismatch', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigP256({ attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;

  const first = await plug.verify(ecItem(zktag), ctxFor(zktag));
  assert.equal(first.valid, true);

  const hijack = ecItem(zktag, { privateKey: ecOther.privateKey, publicKey: ecOther.publicKey });
  const out = await plug.verify(hijack, ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, true, 'a mismatch is a checked answer, not a could-not-check');
  assert.equal(out.valid, false);
  assert.equal(out.reason, 'attester_key_mismatch');
});

test('D38: the SAME device key rebinding under a DIFFERENT zktag (a fresh scan) is first sight again, not a mismatch', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigP256({ attesterStore: store });
  const zktagA = `zktag-${randomBytes(4).toString('hex')}`;
  const zktagB = `zktag-${randomBytes(4).toString('hex')}`;

  assert.equal((await plug.verify(ecItem(zktagA), ctxFor(zktagA))).valid, true);
  const second = await plug.verify(ecItem(zktagB), ctxFor(zktagB));
  assert.equal(second.valid, true);
  assert.deepEqual(second.warnings, ['attester_bound_first_sight'], 'a new zktag is a fresh binding, not a mismatch');
});

// ---------------------------------------------------------------------------
// pubkey / key_id consistency.
// ---------------------------------------------------------------------------

test('D38: a carried pubkey that does not hash to the claimed key_id is sig_key_id_mismatch', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigP256({ attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  const item = ecItem(zktag, { keyId: 'not-the-real-hash' });
  const out = await plug.verify(item, ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.valid, false);
  assert.equal(out.reason, 'sig_key_id_mismatch');
});

test('D38: keyIdFor matches the documented construction -- sha256(der) hex, first 16 chars', () => {
  const der = ecKeys.publicKey.export({ type: 'spki', format: 'der' });
  const id = keyIdFor(der);
  assert.equal(id.length, 16);
  assert.match(id, /^[0-9a-f]{16}$/);
  assert.equal(id, keyIdFor(der), 'deterministic');
});

// ---------------------------------------------------------------------------
// Unpinned, no usable pubkey.
// ---------------------------------------------------------------------------

test('D38: item without pubkey and not pinned -- sig_unknown_key, even with a store configured', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  const plug = sigP256({ keys: [{ key_id: 'someone-else', pubkey: ecKeys.publicKey }], attesterStore: store });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  const item = ecItem(zktag, { keyId: 'not-pinned-and-no-pubkey', includePubkey: false });
  const out = await plug.verify(item, ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, true);
  assert.equal(out.valid, false);
  assert.equal(out.reason, 'sig_unknown_key');
});

test('D38: unpinned item carrying a pubkey, but NO attesterStore configured -- sig_unknown_key (pre-D38 behaviour preserved)', async () => {
  const plug = sigP256({ keys: [{ key_id: 'someone-else', pubkey: ecKeys.publicKey }] });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  const out = await plug.verify(ecItem(zktag), ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.valid, false);
  assert.equal(out.reason, 'sig_unknown_key');
});

// ---------------------------------------------------------------------------
// Store failures -> ok:false, allowed:null, never a throw out of verify().
// ---------------------------------------------------------------------------

test('D38: attesterStore.get() throwing -> ok:false, valid:null (never allowed:false)', async () => {
  const brokenStore = {
    async get() { throw new Error('backend down'); },
    async bind() { /* unreached */ },
  };
  const plug = sigP256({ attesterStore: brokenStore });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  const out = await plug.verify(ecItem(zktag), ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.valid, null);
  assert.equal(out.reason, 'attester_store_unreachable');
});

test('D38: attesterStore.bind() throwing on first sight -> ok:false, valid:null; bind is attempted only AFTER a successful verify', async () => {
  let getCalls = 0;
  const brokenBindStore = {
    async get() { getCalls += 1; return undefined; },
    async bind() { throw new Error('backend down'); },
  };
  const plug = sigP256({ attesterStore: brokenBindStore });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  const out = await plug.verify(ecItem(zktag), ctxFor(zktag));
  assertInvariant(out);
  assert.equal(out.ok, false);
  assert.equal(out.valid, null);
  assert.equal(out.reason, 'attester_store_unreachable');
  assert.equal(getCalls, 1);
});

test('D38: bind is never attempted when the signature does not verify', async () => {
  let bindCalls = 0;
  const spyStore = {
    async get() { return undefined; },
    async bind() { bindCalls += 1; },
  };
  const plug = sigP256({ attesterStore: spyStore });
  const zktag = `zktag-${randomBytes(4).toString('hex')}`;
  // Wrong private key -> the carried pubkey does not match the signature.
  const item = ecItem(zktag, { privateKey: ecOther.privateKey });
  const out = await plug.verify(item, ctxFor(zktag));
  assert.equal(out.ok, true);
  assert.equal(out.valid, false);
  assert.equal(out.reason, 'sig_invalid');
  assert.equal(bindCalls, 0, 'a failed verify never binds');
});

// ---------------------------------------------------------------------------
// Registration.
// ---------------------------------------------------------------------------

test('D38 registration: an attesterStore alone (no pinned keys) is a valid registration', () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  assert.doesNotThrow(() => sigP256({ attesterStore: store }));
  assert.doesNotThrow(() => sigEd25519({ attesterStore: store }));
});

test('D38 registration: neither keys nor attesterStore still throws (pre-D38 behaviour preserved)', () => {
  assert.throws(() => sigP256({}), /non-empty keys list/);
  assert.throws(() => sigP256({ keys: [] }), /non-empty keys list/);
});

test('D38 registration: a malformed attesterStore (missing get/bind) throws at boot', () => {
  assert.throws(() => sigP256({ attesterStore: {} }), /attesterStore must implement/);
  assert.throws(() => sigP256({ attesterStore: { get() {} } }), /attesterStore must implement/);
  assert.throws(() => sigP256({ attesterStore: { get() {}, bind: 'nope' } }), /attesterStore must implement/);
});

// ---------------------------------------------------------------------------
// InMemoryAttesterStore itself.
// ---------------------------------------------------------------------------

test('InMemoryAttesterStore: get() is undefined before any bind(); bind() then get() round-trips', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  assert.equal(await store.get({ scope: SCOPE, zktag: 'zk-1' }), undefined);
  const pubkey = Buffer.from('fake-der-bytes');
  await store.bind({
    scope: SCOPE, zktag: 'zk-1', key_id: 'k1', pubkey,
  });
  const got = await store.get({ scope: SCOPE, zktag: 'zk-1' });
  assert.equal(got.key_id, 'k1');
  assert.ok(got.pubkey.equals(pubkey));
  // A different zktag under the same scope is a distinct binding slot.
  assert.equal(await store.get({ scope: SCOPE, zktag: 'zk-2' }), undefined);
});

test('InMemoryAttesterStore: (scope, zktag) key construction does not collide across the join point', async () => {
  const store = new InMemoryAttesterStore({ quiet: true });
  await store.bind({
    scope: 'ab', zktag: 'c', key_id: 'k1', pubkey: Buffer.from('x'),
  });
  // Without a length prefix, ('ab','c') and ('a','bc') would collide on a
  // naive `${scope}${zktag}` join -- assert they do NOT.
  assert.equal(await store.get({ scope: 'a', zktag: 'bc' }), undefined);
  assert.notEqual(await store.get({ scope: 'ab', zktag: 'c' }), undefined);
});
