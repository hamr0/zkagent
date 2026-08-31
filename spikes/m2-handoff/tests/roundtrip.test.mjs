// node --test — end-to-end tests against the REAL HTTP server on an ephemeral port.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { startServer } from '../server.mjs';

const pExecFile = promisify(execFile);
const walletScript = fileURLToPath(new URL('../scripts/fake-wallet.mjs', import.meta.url));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let srv;
before(async () => { srv = await startServer(0); });
after(async () => { await srv.close(); });

function assertVerdictInvariant(verdict) {
  // M1 spec §3: ok:false forces allowed:null — {ok:false, allowed:false} must never exist.
  if (verdict.ok === false) assert.equal(verdict.allowed, null);
}

async function createTx(body = {}) {
  const res = await fetch(`${srv.url}/ui/presentations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
  });
  assert.equal(res.status, 201);
  return res.json();
}

async function fetchRequestObject(tx) {
  const res = await fetch(tx.request_uri);
  assert.equal(res.status, 200);
  return res.json();
}

function buildPresentation(challenge) {
  return {
    spec: 'zkagent/1', tier: 'A',
    claim: { over_threshold: true, threshold: challenge.threshold },
    challenge, evidence: [],
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

test('happy roundtrip: request -> request.jwt -> direct_post -> poll => allowed:true', async () => {
  const tx = await createTx();
  assert.ok(tx.transactionId && tx.requestId && tx.request_uri);
  assert.match(tx.app_link_https, /^https:\/\/.+request_uri=/);
  assert.match(tx.app_link_av, /^av:\/\/authorize\?/);

  // pending before any response
  assert.deepEqual(await poll(tx), { status: 'pending' });

  const requestObject = await fetchRequestObject(tx);
  assert.equal(requestObject.response_mode, 'direct_post');
  assert.equal(requestObject.client_id_scheme, 'redirect_uri');
  assert.equal(requestObject.client_id, requestObject.response_uri);
  assert.equal(requestObject.dcql_query.credentials[0].meta.doctype_value, 'eu.europa.ec.av.1');
  assert.equal(requestObject.zkagent.spec, 'zkagent/1');
  assert.equal(requestObject.zkagent.tier, 'A');
  assert.deepEqual(requestObject.zkagent.evidence, []); // D27 bare evidence set
  assert.equal(requestObject.zkagent.challenge.threshold, 18);

  const dp = await directPost(requestObject, buildPresentation(requestObject.zkagent.challenge));
  assert.equal(dp.status, 200);

  const status = await poll(tx);
  assert.equal(status.status, 'done');
  assertVerdictInvariant(status.verdict);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, true);
  assert.equal(status.verdict.tier, 'A');
  assert.deepEqual(status.verdict.evidence, []);
});

test('negative: tampered challenge => verdict is a real no (nonce_forged), poll never allowed:true', async () => {
  const tx = await createTx();
  const requestObject = await fetchRequestObject(tx);
  const challenge = { ...requestObject.zkagent.challenge, expires_at: requestObject.zkagent.challenge.expires_at + 60_000 };

  const dp = await directPost(requestObject, buildPresentation(challenge));
  assert.equal(dp.status, 200);

  // Poll repeatedly: allowed:true must never appear.
  for (let i = 0; i < 3; i++) {
    const status = await poll(tx);
    assert.equal(status.status, 'done');
    assertVerdictInvariant(status.verdict);
    assert.notEqual(status.verdict.allowed, true);
    // Observed library semantics: a checked-and-failed challenge is a REAL no
    // ({ok:true, allowed:false, reason:'nonce_forged'}), not ok:false —
    // ok:false is reserved for "could not check" and always carries allowed:null.
    assert.equal(status.verdict.ok, true);
    assert.equal(status.verdict.allowed, false);
    assert.equal(status.verdict.reason, 'nonce_forged');
  }
});

test('negative: expired challenge => allowed:false (challenge_expired)', async () => {
  const tx = await createTx({ ttlMs: 60 }); // spike-only affordance
  const requestObject = await fetchRequestObject(tx);
  await sleep(150);
  const dp = await directPost(requestObject, buildPresentation(requestObject.zkagent.challenge));
  assert.equal(dp.status, 200);
  const status = await poll(tx);
  assert.equal(status.status, 'done');
  assertVerdictInvariant(status.verdict);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, false);
  assert.equal(status.verdict.reason, 'challenge_expired');
});

test("negative: another transaction's challenge under this state => state_challenge_mismatch", async () => {
  const tx1 = await createTx();
  const tx2 = await createTx();
  const ro1 = await fetchRequestObject(tx1);
  const ro2 = await fetchRequestObject(tx2);
  // Answer tx2's state with tx1's (validly sealed) challenge.
  const dp = await directPost(ro2, buildPresentation(ro1.zkagent.challenge));
  assert.equal(dp.status, 200);
  const status = await poll(tx2);
  assert.equal(status.verdict.ok, true);
  assert.equal(status.verdict.allowed, false);
  assert.equal(status.verdict.reason, 'state_challenge_mismatch');
});

test('wire errors are HTTP errors, never verdicts', async () => {
  // unknown state
  const dp = await fetch(`${srv.url}/wallet/direct_post`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ state: 'nope', vp_token: 'eyJ9' }).toString(),
  });
  assert.equal(dp.status, 404);
  // unknown transaction poll
  assert.equal((await fetch(`${srv.url}/ui/presentations/nope`)).status, 404);
  // unknown request.jwt
  assert.equal((await fetch(`${srv.url}/wallet/request.jwt/nope`)).status, 404);
  // second direct_post to a finished transaction is refused at the wire
  const tx = await createTx();
  const ro = await fetchRequestObject(tx);
  await directPost(ro, buildPresentation(ro.zkagent.challenge));
  const again = await directPost(ro, buildPresentation(ro.zkagent.challenge));
  assert.equal(again.status, 409);
});

test('fake-wallet script: valid mode exits 0 with allowed=true', async () => {
  const { stdout } = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--mode', 'valid']);
  assert.match(stdout, /RESULT tier=A mode=valid: allowed=true reason=no-evidence-required -> AS EXPECTED/);
});

test('fake-wallet script: tamper mode exits 0 with allowed=false (nonce_forged)', async () => {
  const { stdout } = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--mode', 'tamper']);
  assert.match(stdout, /RESULT tier=A mode=tamper: allowed=false reason=nonce_forged -> AS EXPECTED/);
});
