// node --test — mode-B (D30) roundtrips: sig-ed25519/1 as the default mode-B
// evidence delivery, against the REAL HTTP server on an ephemeral port.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { createPrivateKey, createPublicKey, generateKeyPairSync, sign as edSign } from 'node:crypto';
import { startServer } from '../server.mjs';
import { sigMessage } from '../sig-ed25519-plug.mjs';
import { DEV_ATTESTER } from '../dev-attester-key.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';

const pExecFile = promisify(execFile);
const walletScript = fileURLToPath(new URL('../scripts/fake-wallet.mjs', import.meta.url));
const SCOPE_DOMAIN = 'm2-handoff.test';
const SYNTHETIC_ZKTAG = 'SYNTHETIC-DEV-ZKTAG-test';

let srv;
before(async () => { srv = await startServer(0); });
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
  const sig = edSign(null, sigMessage(claim, nonce, SCOPE_DOMAIN, zktag), privateKey).toString('base64');
  return [{ type: 'sig-ed25519', version: 1, data: { key_id: keyId, sig } }];
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
  assert.deepEqual(ro.zkagent.evidence_required, ['sig-ed25519/1']); // D30
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
