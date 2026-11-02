// node --test — D122: the demo can pose as a second site at `localhost` (item 24 rows 17/25/26). A request minted for
// Host `localhost:<port>` must verify with scope `localhost`; one minted for `127.0.0.1:<port>` with `127.0.0.1`; and an
// answer signed for the other origin must be refused. Driven over the real server with an explicit Host header (the
// listen socket stays on 127.0.0.1), signing with the dev attester key like tier-b.test.mjs does.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createPrivateKey, createPublicKey, sign as edSign } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { request as httpRequest } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { sigEd25519Message } from 'chiproof';
import { startServer } from '../server.mjs';
import { DEV_ATTESTER } from '../dev-attester-key.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';

let storeDir;
let srv;
before(async () => {
  storeDir = mkdtempSync(join(tmpdir(), 'zkagent-demo-origin-'));
  process.env.DEMO_STORE_PATH = join(storeDir, 'store.json');
  srv = await startServer(0);
});
after(async () => {
  await srv.close();
  delete process.env.DEMO_STORE_PATH;
  rmSync(storeDir, { recursive: true, force: true });
});

/** One HTTP call to the server's real socket, with the Host header a browser/app on `hostName` would send. */
function call(hostName, method, path, { body, headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const r = httpRequest({
      host: '127.0.0.1', port: srv.port, path, method,
      headers: { host: `${hostName}:${srv.port}`, ...(body ? { 'content-length': Buffer.byteLength(body) } : {}), ...headers },
    }, (res) => {
      let t = '';
      res.on('data', (c) => { t += c; });
      res.on('end', () => resolve({ status: res.statusCode, text: t }));
    });
    r.on('error', reject);
    r.end(body);
  });
}
const pathOf = (u) => new URL(u).pathname;

async function mint(hostName, mode) {
  const res = await call(hostName, 'POST', '/ui/presentations', { body: JSON.stringify({ mode }), headers: { 'content-type': 'application/json' } });
  assert.equal(res.status, 201);
  const tx = JSON.parse(res.text);
  assert.ok(tx.request_uri.startsWith(`http://${hostName}:${srv.port}/`), 'links are minted for the Host the page was opened on');
  const ro = await call(hostName, 'GET', pathOf(tx.request_uri));
  assert.equal(ro.status, 200);
  const v = verifyJws(ro.text, createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem));
  assert.equal(v.valid, true);
  assert.ok(v.payload.response_uri.startsWith(`http://${hostName}:${srv.port}/`));
  return { tx, ro: v.payload };
}

async function answer(hostName, ro, presentation) {
  const vp = Buffer.from(JSON.stringify(presentation)).toString('base64url');
  const res = await call(hostName, 'POST', pathOf(ro.response_uri), {
    body: new URLSearchParams({ state: ro.state, vp_token: vp }).toString(),
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
  });
  assert.equal(res.status, 200);
}
async function verdictOf(hostName, tx) {
  const res = await call(hostName, 'GET', `/ui/presentations/${tx.transactionId}`);
  return JSON.parse(res.text);
}

let zktagCounter = 0;
function presentationB(ro, signScope) {
  const challenge = ro.zkagent.challenge;
  const claim = { over_threshold: true, threshold: challenge.threshold };
  const zktag = `SYNTHETIC-DEV-ZKTAG-origin-${++zktagCounter}`;
  const sig = edSign(null, sigEd25519Message(claim, challenge.nonce, signScope, zktag), createPrivateKey(DEV_ATTESTER.privateKeyPem)).toString('base64');
  return { spec: 'zkagent/1', tier: 'B', claim, challenge, zktag, evidence: [{ type: 'sig-ed25519', version: 1, data: { key_id: DEV_ATTESTER.key_id, sig } }] };
}
function presentationA(ro) {
  const challenge = ro.zkagent.challenge;
  return { spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: challenge.threshold }, challenge, evidence: [] };
}

for (const host of ['localhost', '127.0.0.1']) {
  test(`tier A minted for ${host}: answered => allowed:true`, async () => {
    const { tx, ro } = await mint(host, 'A');
    await answer(host, ro, presentationA(ro));
    const st = await verdictOf(host, tx);
    assert.equal(st.verdict.ok, true);
    assert.equal(st.verdict.allowed, true);
  });

  test(`tier B minted for ${host}, signed with scope ${host}: allowed:true`, async () => {
    const { tx, ro } = await mint(host, 'B');
    await answer(host, ro, presentationB(ro, host));
    const st = await verdictOf(host, tx);
    assert.equal(st.verdict.ok, true);
    assert.equal(st.verdict.allowed, true);
    assert.equal(st.verdict.reason, 'evidence-verified');
  });
}

test('cross-origin negative: tier B minted for localhost but signed as if for 127.0.0.1 is refused (sig_invalid), and the right scope is accepted', async () => {
  const wrong = await mint('localhost', 'B');
  await answer('localhost', wrong.ro, presentationB(wrong.ro, '127.0.0.1'));
  const bad = (await verdictOf('localhost', wrong.tx)).verdict;
  assert.equal(bad.ok, true);
  assert.equal(bad.allowed, false, 'a localhost request answered with the 127.0.0.1 scope must not pass');
  assert.equal(bad.reason, 'sig_invalid');
  // The guard excludes something: the identical flow with the right scope passes.
  const right = await mint('localhost', 'B');
  await answer('localhost', right.ro, presentationB(right.ro, 'localhost'));
  assert.equal((await verdictOf('localhost', right.tx)).verdict.allowed, true);
});

test('cross-origin negative: tier B minted for 127.0.0.1 but signed as if for localhost is refused (sig_invalid)', async () => {
  const { tx, ro } = await mint('127.0.0.1', 'B');
  await answer('127.0.0.1', ro, presentationB(ro, 'localhost'));
  const v = (await verdictOf('127.0.0.1', tx)).verdict;
  assert.equal(v.allowed, false);
  assert.equal(v.reason, 'sig_invalid');
});
