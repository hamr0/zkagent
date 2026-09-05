// node --test — the §6.5 S1 POC's test-only `threshold` query param
// (server.mjs, POST /ui/presentations): proves it (a) leaves the real
// THRESHOLD/page default untouched, (b) accepts a different PRESET value,
// (c) accepts the one deliberately off-list value (43) used for the
// not-a-preset negative test, and (d) ignores anything else. Against the
// REAL HTTP server on an ephemeral port, same pattern as roundtrip.test.mjs.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createPublicKey } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { startServer } from '../server.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';

let storeDir;
let srv;
before(async () => {
  storeDir = mkdtempSync(join(tmpdir(), 'zkagent-demo-threshold-override-'));
  process.env.DEMO_STORE_PATH = join(storeDir, 'store.json');
  srv = await startServer(0);
});
after(async () => {
  await srv.close();
  delete process.env.DEMO_STORE_PATH;
  rmSync(storeDir, { recursive: true, force: true });
});

async function createTx(query = '') {
  const res = await fetch(`${srv.url}/ui/presentations${query}`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({}),
  });
  assert.equal(res.status, 201);
  return res.json();
}

async function embeddedThreshold(tx) {
  const res = await fetch(tx.request_uri);
  assert.equal(res.status, 200);
  const jws = await res.text();
  const v = verifyJws(jws, createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem));
  assert.equal(v.valid, true, `request JWS must verify (got ${v.reason})`);
  return v.payload.zkagent.challenge.threshold;
}

test('no threshold query param -> the real default (18) is embedded, unchanged', async () => {
  const tx = await createTx();
  assert.equal(await embeddedThreshold(tx), 18);
});

test('a different preset value (21) overrides the embedded threshold only', async () => {
  const tx = await createTx('?threshold=21');
  assert.equal(await embeddedThreshold(tx), 21);
});

test('the deliberately off-list value (43) is accepted for the not-a-preset negative test', async () => {
  const tx = await createTx('?threshold=43');
  assert.equal(await embeddedThreshold(tx), 43);
});

test('a value outside the allowed override set is ignored, falling back to the real default', async () => {
  const tx = await createTx('?threshold=99');
  assert.equal(await embeddedThreshold(tx), 18);
});

test('requesting the same value as the real default is a no-op, not an error', async () => {
  const tx = await createTx('?threshold=18');
  assert.equal(await embeddedThreshold(tx), 18);
});
