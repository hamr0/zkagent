// node --test — mode-B (D30/D31) roundtrips: sig-ed25519/1 and sig-p256/1 as
// the two co-equal mode-B evidence alternatives, against the REAL HTTP
// server on an ephemeral port.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import {
  createPrivateKey, createPublicKey, generateKeyPairSync, sign as edSign, sign as ecSign,
} from 'node:crypto';
import { sigEd25519Message, sigP256Message, keyIdFor } from 'chiproof';
import { startServer } from '../server.mjs';
import { DEV_ATTESTER } from '../dev-attester-key.mjs';
import { DEV_ATTESTER_P256 } from '../dev-attester-key-p256.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';

const pExecFile = promisify(execFile);
const walletScript = fileURLToPath(new URL('../scripts/fake-wallet.mjs', import.meta.url));
const SYNTHETIC_ZKTAG = 'SYNTHETIC-DEV-ZKTAG-test';

let srv;
// Real-device finding (2026-09-01): this used to be a hardcoded literal
// ('m2-handoff.test') that coincidentally matched the server's own
// independent hardcoded literal -- two copies of the same string, which is
// exactly why this suite missed a live Pixel run failing on scope
// disagreement (`sig_invalid`). SCOPE_DOMAIN is now derived from the
// server's ACTUAL serving host (`srv.url`'s hostname) once the ephemeral
// server is up, the same mechanism `scripts/fake-wallet.mjs` now uses
// (derived from the verified request origin, D37) -- not imported from
// server.mjs, not a literal. If the server's configured scopeDomain and its
// own serving host ever disagree again, every signing test below fails for
// the right reason instead of silently agreeing with itself.
let SCOPE_DOMAIN;
before(async () => {
  srv = await startServer(0);
  SCOPE_DOMAIN = new URL(srv.url).hostname;
});
after(async () => { await srv.close(); });

function assertVerdictInvariant(verdict) {
  if (verdict.ok === false) assert.equal(verdict.allowed, null);
}

async function createTxB() {
  const res = await fetch(`${srv.url}/ui/presentations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ mode: 'B' }),
  });
  assert.equal(res.status, 201);
  return res.json();
}

async function fetchRequestObject(tx) {
  const res = await fetch(tx.request_uri);
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('content-type'), 'application/oauth-authz-req+jwt');
  const jws = await res.text();
  const v = verifyJws(jws, createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem));
  assert.equal(v.valid, true, `request JWS must verify (got ${v.reason})`);
  assert.equal(v.header.alg, 'ES256');
  assert.equal(v.header.typ, 'oauth-authz-req+jwt');
  return v.payload;
}

function buildPresentationB(challenge, { evidence, zktag = SYNTHETIC_ZKTAG } = {}) {
  return {
    spec: 'zkagent/1', tier: 'B',
    claim: { over_threshold: true, threshold: challenge.threshold },
    challenge, zktag, evidence: evidence ?? [],
  };
}

function signEvidence(claim, nonce, zktag, privateKeyPem, keyId = DEV_ATTESTER.key_id) {
  const privateKey = typeof privateKeyPem === 'string' ? createPrivateKey(privateKeyPem) : privateKeyPem;
  const sig = edSign(null, sigEd25519Message(claim, nonce, SCOPE_DOMAIN, zktag), privateKey).toString('base64');
  return [{ type: 'sig-ed25519', version: 1, data: { key_id: keyId, sig } }];
}

/** D31: the sig-p256/1 alternative, signed with the DEV-ONLY P-256 attester key. */
function signEvidenceP256(claim, nonce, zktag, privateKeyPem, keyId = DEV_ATTESTER_P256.key_id) {
  const privateKey = typeof privateKeyPem === 'string' ? createPrivateKey(privateKeyPem) : privateKeyPem;
  const sig = ecSign('sha256', sigP256Message(claim, nonce, SCOPE_DOMAIN, zktag), privateKey).toString('base64');
  return [{ type: 'sig-p256', version: 1, data: { key_id: keyId, sig } }];
}

/**
 * D38: an UNPINNED device key -- carries its own `pubkey` (SubjectPublicKeyInfo
 * DER, base64) alongside a `key_id` computed the same way the real scanner
 * does (`EvidenceSigner.keyIdFor`, mirrored here by chiproof's own `keyIdFor`).
 * Not in the verifier's pinned `keys` list -- this is what a real per-origin
 * device key looks like once the scanner generates a fresh key per site.
 */
function deviceEvidenceP256(claim, nonce, zktag, { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' })) {
  const der = publicKey.export({ type: 'spki', format: 'der' });
  const sig = ecSign('sha256', sigP256Message(claim, nonce, SCOPE_DOMAIN, zktag), privateKey).toString('base64');
  return {
    evidence: [{ type: 'sig-p256', version: 1, data: { key_id: keyIdFor(der), pubkey: der.toString('base64'), sig } }],
    privateKey,
    publicKey,
  };
}

async function directPost(requestObject, presentation) {
  const vpToken = Buffer.from(JSON.stringify(presentation)).toString('base64url');
  return fetch(requestObject.response_uri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ state: requestObject.state, vp_token: vpToken }).toString(),
  });
}

async function poll(tx) {
  const res = await fetch(`${srv.url}/ui/presentations/${tx.transactionId}`);
  assert.equal(res.status, 200);
  return res.json();
}

test('tier-B happy path: valid sig-ed25519/1 => allowed:true, evidence-verified, zktag echoed', async () => {
  const tx = await createTxB();
  assert.equal(tx.mode, 'B');
  const ro = await fetchRequestObject(tx);
  assert.equal(ro.zkagent.tier, 'B');
  // D31: an any-of alternatives group, not a single required plug.
  assert.deepEqual(ro.zkagent.evidence_required, [['sig-ed25519/1', 'sig-p256/1']]);
  assert.equal(ro.zkagent.challenge.tier, 'B');

  const p = buildPresentationB(ro.zkagent.challenge);
  p.evidence = signEvidence(p.claim, p.challenge.nonce, p.zktag, DEV_ATTESTER.privateKeyPem);
  assert.equal((await directPost(ro, p)).status, 200);

  const status = await poll(tx);
  assert.equal(status.status, 'done');
  assertVerdictInvariant(status.verdict);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, true);
  assert.equal(status.verdict.tier, 'B');
  assert.equal(status.verdict.reason, 'evidence-verified');
  assert.deepEqual(status.verdict.evidence, ['sig-ed25519/1']);
  assert.equal(status.verdict.zktag, SYNTHETIC_ZKTAG);
});

test('tier-B happy path (D31 alternative): valid sig-p256/1 alone => allowed:true, evidence-verified, verdict records sig-p256/1 as the plug used', async () => {
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge);
  p.evidence = signEvidenceP256(p.claim, p.challenge.nonce, p.zktag, DEV_ATTESTER_P256.privateKeyPem);
  assert.equal((await directPost(ro, p)).status, 200);

  const status = await poll(tx);
  assert.equal(status.status, 'done');
  assertVerdictInvariant(status.verdict);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, true);
  assert.equal(status.verdict.reason, 'evidence-verified');
  assert.deepEqual(status.verdict.evidence, ['sig-p256/1'], 'the verdict records which alternative was actually used (D31)');
  assert.equal(status.verdict.zktag, SYNTHETIC_ZKTAG);
});

test("tier-B negative: wrong-key signature => plug's valid:false path (sig_invalid), allowed:false", async () => {
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge);
  const { privateKey } = generateKeyPairSync('ed25519'); // NOT the pinned key
  p.evidence = signEvidence(p.claim, p.challenge.nonce, p.zktag, privateKey);
  assert.equal((await directPost(ro, p)).status, 200);

  const status = await poll(tx);
  assertVerdictInvariant(status.verdict);
  // Plug contract observed: checked-and-failed signature is valid:false at the
  // plug => {ok:true, allowed:false} upstream — never ok:false (that is
  // reserved for "could not check" and forces allowed:null).
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, false);
  assert.equal(status.verdict.reason, 'sig_invalid');
});

test('tier-B negative: missing evidence on a tier-B challenge => evidence_required_missing (chiproof contract, asserted explicitly)', async () => {
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge, { evidence: [] });
  assert.equal((await directPost(ro, p)).status, 200);

  const status = await poll(tx);
  assertVerdictInvariant(status.verdict);
  // chiproof's contract for a required-but-absent evidence type: a REAL no.
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, false);
  assert.equal(status.verdict.reason, 'evidence_required_missing');
});

test('tier-B negative: missing zktag on a tier-B presentation => zktag_required (D21)', async () => {
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge, { zktag: undefined });
  delete p.zktag;
  // Presentation carries no zktag at all; sign over SOME zktag string (value
  // is moot — the verifier's zktag_required check fires before the plug ever
  // sees this evidence, so signature validity here is irrelevant).
  p.evidence = signEvidence(p.claim, p.challenge.nonce, SYNTHETIC_ZKTAG, DEV_ATTESTER.privateKeyPem);
  assert.equal((await directPost(ro, p)).status, 200);
  const status = await poll(tx);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, false);
  assert.equal(status.verdict.reason, 'zktag_required');
});

test('fake-wallet script: tier B valid => allowed:true (evidence-verified)', async () => {
  const { stdout } = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--tier', 'B', '--mode', 'valid']);
  assert.match(stdout, /RESULT tier=B mode=valid: allowed=true reason=evidence-verified -> AS EXPECTED/);
  assert.match(stdout, /SYNTHETIC-DEV-ZKTAG/); // synthetic zktag is visibly labeled
});

test('fake-wallet script: tier B wrongkey => allowed:false (sig_invalid)', async () => {
  const { stdout } = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--tier', 'B', '--mode', 'wrongkey']);
  assert.match(stdout, /RESULT tier=B mode=wrongkey: allowed=false reason=sig_invalid -> AS EXPECTED/);
});

test('fake-wallet script: tier B missing evidence => allowed:false (evidence_required_missing)', async () => {
  const { stdout } = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--tier', 'B', '--mode', 'missing']);
  assert.match(stdout, /RESULT tier=B mode=missing: allowed=false reason=evidence_required_missing -> AS EXPECTED/);
});

test('fake-wallet script: tier B firstsight (D38, unpinned device key carrying pubkey) => allowed:true (evidence-verified)', async () => {
  const { stdout } = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--tier', 'B', '--mode', 'firstsight']);
  assert.match(stdout, /RESULT tier=B mode=firstsight: allowed=true reason=evidence-verified -> AS EXPECTED/);
  assert.match(stdout, /trust-on-first-sight, D38/);
});

// ---------------------------------------------------------------------------
// Review finding fix: zktag-swap (a relay rewriting `presentation.zktag`
// after the attester signed, while replaying the attester's original
// signature, must NOT still verify). Closed by v2's `binds.zktag: true`.
// ---------------------------------------------------------------------------

test('tier-B negative: zktag-swap — valid signature over zktag X, presentation carries zktag Y => sig_invalid, allowed:false (a real no)', async () => {
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge);
  const victimZktag = 'SYNTHETIC-DEV-ZKTAG-victim';
  assert.notEqual(p.zktag, victimZktag);
  // Attester legitimately signs for victimZktag; the relay swaps the
  // presentation's zktag to p.zktag afterwards (the presentation object
  // already carries p.zktag from buildPresentationB) and replays the
  // signature unchanged — exactly the rewrite the finding describes.
  p.evidence = signEvidence(p.claim, p.challenge.nonce, victimZktag, DEV_ATTESTER.privateKeyPem);
  assert.equal((await directPost(ro, p)).status, 200);

  const status = await poll(tx);
  assertVerdictInvariant(status.verdict);
  assert.equal(status.verdict.ok, true, 'a swap is a checked answer, not a could-not-check');
  assert.equal(status.verdict.allowed, false, 'zktag-swapped evidence must be a real no');
  assert.equal(status.verdict.reason, 'sig_invalid');
});

// ---------------------------------------------------------------------------
// Server-side collapse: ONE verifier instance now serves both tiers via
// chiproof 0.3.0's per-tier evidence.require (item 2 of this fix).
// ---------------------------------------------------------------------------

test('single-instance property: tier-A bare passes and tier-B-missing-evidence fails, both against the SAME server/verifier object', async () => {
  // Tier A first: bare presentation, no evidence required (D27).
  const resA = await fetch(`${srv.url}/ui/presentations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({}),
  });
  assert.equal(resA.status, 201);
  const txA = await resA.json();
  assert.equal(txA.mode, 'A');
  const roA = await fetchRequestObject(txA);
  const pA = {
    spec: 'zkagent/1', tier: 'A',
    claim: { over_threshold: true, threshold: roA.zkagent.challenge.threshold },
    challenge: roA.zkagent.challenge, evidence: [],
  };
  assert.equal((await directPost(roA, pA)).status, 200);
  const statusA = await poll(txA);
  assertVerdictInvariant(statusA.verdict);
  assert.equal(statusA.verdict.ok, true);
  assert.equal(statusA.verdict.allowed, true);
  assert.equal(statusA.verdict.reason, 'no-evidence-required');

  // Same running server (same `verifier` instance inside createApp) — tier B
  // without the required sig-ed25519/1 evidence still refuses, proving the
  // one instance keeps both tiers' rules simultaneously (no A/B leakage
  // either way).
  const txB = await createTxB();
  const roB = await fetchRequestObject(txB);
  const pB = buildPresentationB(roB.zkagent.challenge, { evidence: [] });
  assert.equal((await directPost(roB, pB)).status, 200);
  const statusB = await poll(txB);
  assertVerdictInvariant(statusB.verdict);
  assert.equal(statusB.verdict.ok, true);
  assert.equal(statusB.verdict.allowed, false);
  assert.equal(statusB.verdict.reason, 'evidence_required_missing');
});

// ---------------------------------------------------------------------------
// D38 (2026-09-01): per-origin device keys, trust-on-first-sight, against
// the REAL HTTP server -- an UNPINNED device key (not in ATTESTER_P256_*)
// that carries its own `pubkey` gets bound on first sight and must match on
// every later presentation for the same zktag.
// ---------------------------------------------------------------------------

test('D38: an unpinned device key is bound on first sight, then matches on a second presentation for the SAME zktag', async () => {
  const device = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const zktag = `SYNTHETIC-DEV-ZKTAG-d38-${Math.random().toString(36).slice(2)}`;

  const tx1 = await createTxB();
  const ro1 = await fetchRequestObject(tx1);
  const p1 = buildPresentationB(ro1.zkagent.challenge, { zktag });
  p1.evidence = deviceEvidenceP256(p1.claim, p1.challenge.nonce, zktag, device).evidence;
  assert.equal((await directPost(ro1, p1)).status, 200);
  const status1 = await poll(tx1);
  assertVerdictInvariant(status1.verdict);
  assert.equal(status1.verdict.ok, true);
  assert.equal(status1.verdict.allowed, true);
  assert.equal(status1.verdict.reason, 'evidence-verified');
  assert.deepEqual(status1.verdict.evidence, ['sig-p256/1']);
  assert.deepEqual(status1.verdict.warnings, ['attester_bound_first_sight']);

  // Second presentation, SAME device key, SAME zktag -- now matches the bound key.
  const tx2 = await createTxB();
  const ro2 = await fetchRequestObject(tx2);
  const p2 = buildPresentationB(ro2.zkagent.challenge, { zktag });
  p2.evidence = deviceEvidenceP256(p2.claim, p2.challenge.nonce, zktag, device).evidence;
  assert.equal((await directPost(ro2, p2)).status, 200);
  const status2 = await poll(tx2);
  assertVerdictInvariant(status2.verdict);
  assert.equal(status2.verdict.ok, true);
  assert.equal(status2.verdict.allowed, true);
  assert.equal(status2.verdict.reason, 'evidence-verified');
  assert.equal('warnings' in status2.verdict, false, 'the second sighting is a match, not first sight -- no note');
});

test('D38: a DIFFERENT unpinned device key presented for an ALREADY-BOUND zktag => attester_key_mismatch', async () => {
  const zktag = `SYNTHETIC-DEV-ZKTAG-d38-mismatch-${Math.random().toString(36).slice(2)}`;

  const tx1 = await createTxB();
  const ro1 = await fetchRequestObject(tx1);
  const p1 = buildPresentationB(ro1.zkagent.challenge, { zktag });
  p1.evidence = deviceEvidenceP256(p1.claim, p1.challenge.nonce, zktag).evidence;
  assert.equal((await directPost(ro1, p1)).status, 200);
  assert.equal((await poll(tx1)).verdict.allowed, true);

  // A DIFFERENT device key for the SAME zktag -- refused, never re-bound.
  const tx2 = await createTxB();
  const ro2 = await fetchRequestObject(tx2);
  const p2 = buildPresentationB(ro2.zkagent.challenge, { zktag });
  p2.evidence = deviceEvidenceP256(p2.claim, p2.challenge.nonce, zktag).evidence;
  assert.equal((await directPost(ro2, p2)).status, 200);
  const status2 = await poll(tx2);
  assertVerdictInvariant(status2.verdict);
  assert.equal(status2.verdict.ok, true, 'a mismatch is a checked answer, not a could-not-check');
  assert.equal(status2.verdict.allowed, false);
  assert.equal(status2.verdict.reason, 'attester_key_mismatch');
});

test('D38: a pubkey that does not hash to its own claimed key_id is sig_key_id_mismatch, allowed:false', async () => {
  const zktag = `SYNTHETIC-DEV-ZKTAG-d38-badid-${Math.random().toString(36).slice(2)}`;
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge, { zktag });
  const { evidence } = deviceEvidenceP256(p.claim, p.challenge.nonce, zktag);
  evidence[0].data.key_id = 'not-the-real-hash';
  p.evidence = evidence;
  assert.equal((await directPost(ro, p)).status, 200);
  const status = await poll(tx);
  assertVerdictInvariant(status.verdict);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, false);
  assert.equal(status.verdict.reason, 'sig_key_id_mismatch');
});

test('D38: the pinned ATTESTER_P256_* env-style key path is unaffected by the attesterStore -- pinned items still verify without pubkey', async () => {
  // Unchanged from the pre-D38 tier-B happy path test above, replayed here
  // explicitly under the D38 section to confirm the pinned path was not
  // disturbed by wiring the store into the same plug instance.
  const tx = await createTxB();
  const ro = await fetchRequestObject(tx);
  const p = buildPresentationB(ro.zkagent.challenge);
  p.evidence = signEvidenceP256(p.claim, p.challenge.nonce, p.zktag, DEV_ATTESTER_P256.privateKeyPem);
  assert.equal((await directPost(ro, p)).status, 200);
  const status = await poll(tx);
  assert.equal(status.verdict.allowed, true);
  assert.equal('warnings' in status.verdict, false, 'the pinned path never carries the first-sight note');
});
