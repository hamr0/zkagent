// node --test — milestones.md §6.9.2 item 24 (D111, D122): the demo page as the local test and showcase page.
// Pass criteria 1, 2, 3, 5 plus the localhost-origin creation behind rows 17/25/26 and the helper script's new flags.
// The row table below is written out independently of scenarios.mjs (from the milestones table), so a page that lost a
// row, a one-liner or a "needs" marking cannot agree with itself.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createPrivateKey, createPublicKey, sign as edSign } from 'node:crypto';
import { execFile } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { sigEd25519Message } from 'chiproof';
import { startServer } from '../server.mjs';
import { DEV_ATTESTER } from '../dev-attester-key.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';

let storeDir;
let srv;
before(async () => {
  storeDir = mkdtempSync(join(tmpdir(), 'zkagent-demo-scenarios-'));
  process.env.DEMO_STORE_PATH = join(storeDir, 'store.json');
  delete process.env.DEMO_TEST_MODE;
  delete process.env.TIERC_POC_ALLOW_RAW;
  srv = await startServer(0);
});
after(async () => {
  await srv.close();
  delete process.env.DEMO_STORE_PATH;
  delete process.env.DEMO_TEST_MODE;
  rmSync(storeDir, { recursive: true, force: true });
});

// Row -> words its "needs" marking must show (from the milestones table), and whether the row is test-only.
const EXPECTED_NEEDS = {
  1: ['Phone'], 2: ['Phone'], 3: ['Phone'], 4: ['Phone'], 5: ['Phone'],
  6: ['Phone', 'Test-only'], 7: ['Phone', 'Build', 'Test-only'],
  8: ['Phone', 'Build'], 9: ['row 8'], 10: ['row 8'], 11: ['row 8'], 12: ['row 8'], 13: ['row 8'],
  14: ['Phone'], 15: ['Phone', 'Test-only'], 16: ['Phone', 'Test-only'], 17: ['Phone', 'Build'], 18: ['Phone', 'Build'],
  19: ['above'], 20: ['Phone'], 21: ['Phone'], 22: ['Phone'], 23: ['Phone'], 24: ['Phone'],
  25: ['Phone', 'Build'], 26: ['row 25', 'Build'],
};
const TEST_ONLY_ROWS = [6, 7, 15, 16];
const ROWS = Array.from({ length: 26 }, (_, i) => i + 1);

async function page(testMode) {
  if (testMode) process.env.DEMO_TEST_MODE = '1'; else delete process.env.DEMO_TEST_MODE;
  try {
    const res = await fetch(`${srv.url}/`);
    assert.equal(res.status, 200);
    return await res.text();
  } finally { delete process.env.DEMO_TEST_MODE; }
}

/** Rows (1..26) that have no button carrying their data-row attribute. */
function rowsWithoutControl(html) {
  return ROWS.filter((n) => !new RegExp(`<button[^>]*data-row="${n}"`).test(html));
}
const textOf = (html, re) => (html.match(re)?.[1] ?? '').replace(/&quot;/g, '"').replace(/&#39;/g, "'");

test('(1) test mode ON: every one of the 26 rows has a control on the page', async () => {
  const html = await page(true);
  assert.deepEqual(rowsWithoutControl(html), []);
});

test('(1) negative control: the checker turns red when one control is removed from the page', async () => {
  const html = await page(true);
  const stripped = html.replace(/<button[^>]*data-row="17"[^>]*>[^<]*<\/button>/g, '');
  assert.notEqual(stripped, html, 'the removal must actually remove something');
  assert.deepEqual(rowsWithoutControl(stripped), [17], 'row 17 is reported missing');
});

test('(1) test mode OFF: every non-test-only row still has its control, the four test-only rows are listed without one', async () => {
  const html = await page(false);
  assert.deepEqual(rowsWithoutControl(html), TEST_ONLY_ROWS);
  for (const n of TEST_ONLY_ROWS) assert.ok(html.includes(`data-row-item="${n}"`), `row ${n} stays listed`);
});

test('(2) every row has its one-line explanation and its needs marking visible, in both modes', async () => {
  for (const mode of [true, false]) {
    const html = await page(mode);
    for (const n of ROWS) {
      const line = textOf(html, new RegExp(`data-row-line="${n}">([^<]*)<`));
      assert.ok(line.length >= 30 && line.length <= 330, `row ${n} has a one-line explanation (got ${line.length} chars)`);
      const needs = textOf(html, new RegExp(`data-row-needs="${n}">([^<]*)<`));
      assert.match(needs, /^Needs: /, `row ${n} shows a needs marking`);
      for (const w of EXPECTED_NEEDS[n]) assert.ok(needs.includes(w), `row ${n} needs marking mentions "${w}" (got "${needs}")`);
    }
  }
});

test('(2) rows 17, 25 and 26 name the second site plainly; 17/18/26 say the app tells an unapproved site nothing', async () => {
  const html = await page(false);
  const line = (n) => textOf(html, new RegExp(`data-row-line="${n}">([^<]*)<`));
  for (const n of [17, 25]) assert.match(line(n), /the same demo posing as an unlisted site/);
  for (const n of [17, 18, 26]) assert.match(line(n), /No answer/);
  for (const n of [17, 18, 19, 26]) assert.match(line(n), /tells .*nothing|tells an unapproved site nothing/);
  assert.match(line(19), /rows 14 to 16 show the one generic "Refused"/);
  // The generic "Refused" rows must not claim "No answer".
  for (const n of [14, 15, 16]) assert.match(line(n), /"Refused"/);
});

test('(3) test mode OFF: no test-only control on the page, and the server refuses a test-only request shape', async () => {
  const on = await page(true);
  const off = await page(false);
  const countTestOnly = (h) => (h.match(/data-test-only="1"/g) ?? []).length;
  const excluded = countTestOnly(on) - countTestOnly(off);
  assert.ok(excluded > 0, 'the guard must exclude something: test mode ON has test-only controls that OFF lacks');
  assert.equal(countTestOnly(off), 0);
  assert.equal(off.includes('testModeBanner'), false);
  assert.ok(on.includes('testModeBanner'));
  // Server side, test mode OFF: both test-only shapes are refused, the plain request is not.
  delete process.env.DEMO_TEST_MODE;
  const post = (query, body) => fetch(`${srv.url}/ui/presentations${query}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  const second = await post('?threshold=21', { mode: 'A' });
  assert.equal(second.status, 400);
  assert.deepEqual(await second.json(), { error: 'test_mode_off' });
  const raw = await post('', { mode: 'C', rawPredicates: [{ name: 'dob_at_least', age: 17 }] });
  assert.equal(raw.status, 400);
  assert.deepEqual(await raw.json(), { error: 'test_mode_off' });
  assert.equal((await post('', { mode: 'A' })).status, 201);
  assert.equal((await post('', { mode: 'C', predicates: [{ name: 'expiry_beyond', months: 3 }] })).status, 201);
  // Same shapes, test mode ON: accepted.
  process.env.DEMO_TEST_MODE = '1';
  try {
    assert.equal((await post('?threshold=21', { mode: 'A' })).status, 201);
    assert.equal((await post('', { mode: 'C', rawPredicates: [{ name: 'dob_at_least', age: 17 }] })).status, 201);
  } finally { delete process.env.DEMO_TEST_MODE; }
});

test('(3) the test-only controls send the shapes the server gates (second age, raw question) and nothing else is test-only', async () => {
  const on = await page(true);
  const testOnlyRows = [...new Set([...on.matchAll(/<button[^>]*data-test-only="1"[^>]*data-row="(\d+)"|<button[^>]*data-row="(\d+)"[^>]*data-test-only="1"/g)].map((m) => Number(m[1] ?? m[2])))].sort((a, b) => a - b);
  assert.deepEqual(testOnlyRows, TEST_ONLY_ROWS);
  assert.match(on, /data-row="6"[^>]*data-pick="threshold"/);
  assert.match(on, /data-row="15"[^>]*data-pick="raw"/);
  assert.match(on, /data-row="16"[^>]*data-pick="age"/);
});

test('(5) the lead no longer says "Two things" and covers everything the page can ask', async () => {
  const html = await page(false);
  const lead = textOf(html, /<p class="lead">([^<]*)<\/p>/);
  assert.ok(lead.length > 0, 'the lead is present');
  assert.doesNotMatch(html, /Two things/);
  assert.match(lead, /age/);
  assert.match(lead, /unique adult/);
  assert.match(lead, /specific facts about a document/);
});

test('the M3 item 6 default stands outside test mode: no threshold picker on the page, default request embeds 18', async () => {
  const html = await page(false);
  assert.equal(/data-pick="threshold"/.test(html), false);
  const tx = await (await fetch(`${srv.url}/ui/presentations`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' })).json();
  const jws = await (await fetch(tx.request_uri)).text();
  assert.equal(verifyJws(jws, createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem)).payload.zkagent.challenge.threshold, 18);
});

test('the inline page script is valid JavaScript (node --check on the extracted module)', async () => {
  const html = await page(true);
  const m = html.match(/<script type="module">([\s\S]*?)<\/script>/);
  assert.ok(m, 'the page has its module script');
  const f = join(storeDir, 'page-script.mjs');
  writeFileSync(f, m[1]);
  await promisify(execFile)(process.execPath, ['--check', f]);
});

// ---- rows 17, 25, 26: the request is created for the localhost origin even though the page is at 127.0.0.1 --------

const post = (body, host) => fetch(`${srv.url}/ui/presentations`, { method: 'POST', headers: { 'content-type': 'application/json', ...(host ? { host } : {}) }, body: JSON.stringify(body) });

async function requestObjectOf(tx) {
  const jws = await (await fetch(tx.request_uri.replace(/^http:\/\/[^/]+/, srv.url))).text();
  const v = verifyJws(jws, createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem));
  assert.equal(v.valid, true);
  return v.payload;
}

test('as_host=localhost: every tier is minted for http://localhost:<port> from a page opened at 127.0.0.1', async () => {
  for (const body of [{ mode: 'A' }, { mode: 'B' }, { mode: 'C', predicates: [{ name: 'expiry_beyond', months: 3 }] }]) {
    const res = await post({ ...body, as_host: 'localhost' });
    assert.equal(res.status, 201, JSON.stringify(body.mode));
    const tx = await res.json();
    assert.ok(tx.request_uri.startsWith(`http://localhost:${srv.port}/`), tx.request_uri);
    assert.ok(tx.app_link_av.includes(encodeURIComponent(`http://localhost:${srv.port}/wallet/direct_post`)), 'the av link carries the localhost response_uri');
    const ro = await requestObjectOf(tx);
    assert.equal(ro.response_uri, `http://localhost:${srv.port}/wallet/direct_post`);
    assert.equal(ro.client_id, ro.response_uri);
  }
  // Without as_host the same page origin keeps 127.0.0.1.
  const plain = await (await post({ mode: 'A' })).json();
  assert.ok(plain.request_uri.startsWith(`http://127.0.0.1:${srv.port}/`));
});

test('as_host=localhost: a tier-B answer signed for scope localhost verifies (the transaction keeps its scope); one signed for 127.0.0.1 does not', async () => {
  const answer = async (ro, scope, n) => {
    const challenge = ro.zkagent.challenge;
    const claim = { over_threshold: true, threshold: challenge.threshold };
    const zktag = `SYNTHETIC-DEV-ZKTAG-aslocalhost-${n}`;
    const sig = edSign(null, sigEd25519Message(claim, challenge.nonce, scope, zktag), createPrivateKey(DEV_ATTESTER.privateKeyPem)).toString('base64');
    const presentation = { spec: 'zkagent/1', tier: 'B', claim, challenge, zktag, evidence: [{ type: 'sig-ed25519', version: 1, data: { key_id: DEV_ATTESTER.key_id, sig } }] };
    const r = await fetch(`${srv.url}${new URL(ro.response_uri).pathname}`, {
      method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ state: ro.state, vp_token: Buffer.from(JSON.stringify(presentation)).toString('base64url') }).toString(),
    });
    assert.equal(r.status, 200);
  };
  const good = await (await post({ mode: 'B', as_host: 'localhost' })).json();
  await answer(await requestObjectOf(good), 'localhost', 1);
  const ok = (await (await fetch(`${srv.url}/ui/presentations/${good.transactionId}`)).json()).verdict;
  assert.equal(ok.allowed, true);
  const bad = await (await post({ mode: 'B', as_host: 'localhost' })).json();
  await answer(await requestObjectOf(bad), '127.0.0.1', 2);
  const no = (await (await fetch(`${srv.url}/ui/presentations/${bad.transactionId}`)).json()).verdict;
  assert.equal(no.allowed, false);
  assert.equal(no.reason, 'sig_invalid');
});

test('as_host: any other value is a 400, and a non-loopback Host is a 403', async () => {
  for (const v of ['example.com', '127.0.0.1', 'LOCALHOST', 42, '']) {
    const res = await post({ mode: 'A', as_host: v });
    assert.equal(res.status, 400, String(v));
    assert.deepEqual(await res.json(), { error: 'as_host_invalid' });
  }
  // fetch() forbids overriding Host, so drive the server with a raw http request.
  const { request } = await import('node:http');
  const status = await new Promise((resolve, reject) => {
    const r = request({ host: '127.0.0.1', port: srv.port, path: '/ui/presentations', method: 'POST', headers: { host: 'demo.example.org', 'content-type': 'application/json' } }, (res) => { res.resume(); res.on('end', () => resolve(res.statusCode)); });
    r.on('error', reject);
    r.end(JSON.stringify({ mode: 'A', as_host: 'localhost' }));
  });
  assert.equal(status, 403);
});

test('the page controls for rows 17, 25 and 26 carry the localhost host; rows 17, 18 and 26 carry the no-reply expectation; no other row does', async () => {
  const html = await page(true);
  const rowsWith = (attr) => [...new Set([...html.matchAll(new RegExp(`<button[^>]*data-row="(\\d+)"[^>]*${attr}`, 'g'))].map((m) => Number(m[1])))].sort((a, b) => a - b);
  assert.deepEqual(rowsWith('data-host="localhost"'), [17, 25, 26]);
  assert.deepEqual(rowsWith('data-expect="no-reply"'), [17, 18, 26]);
  // The page script wires both attributes to the request it sends.
  assert.match(html, /payload\.as_host = opts\.host/);
  assert.match(html, /currentExpect === 'no-reply'/);
});

// ---- finding #46: the helper script gets --threshold (and --as-host) ----------------------------------------------

const SCRIPT = fileURLToPath(new URL('../scripts/tierc-poc-request.mjs', import.meta.url));
const run = (args) => promisify(execFile)(process.execPath, [SCRIPT, ...args], { env: { ...process.env }, encoding: 'utf8' });

test('#46: the helper sends --threshold for tier A (test mode on) and the verifier embeds it; with test mode off it exits 1 with test_mode_off', async () => {
  const u = new URL(srv.url);
  const base = ['--tier', 'A', '--host', u.hostname, '--port', u.port];
  process.env.DEMO_TEST_MODE = '1';
  try {
    const { stdout } = await run([...base, '--threshold', '21']);
    assert.match(stdout, /threshold=21/);
    const link = stdout.split('\n').find((l) => l.startsWith("'av://"));
    const requestUri = new URL(link.slice(1, -1)).searchParams.get('request_uri');
    const jws = await (await fetch(requestUri)).text();
    assert.equal(verifyJws(jws, createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem)).payload.zkagent.challenge.threshold, 21);
  } finally { delete process.env.DEMO_TEST_MODE; }
  await assert.rejects(run([...base, '--threshold', '21']), (e) => e.code === 1 && /test_mode_off/.test(e.stderr));
  await assert.rejects(run(['--tier', 'C', '--host', u.hostname, '--port', u.port, '--threshold', '21']), (e) => e.code === 2);
});

test('#46: the helper --as-host localhost mints a localhost-origin link', async () => {
  const u = new URL(srv.url);
  const { stdout } = await run(['--tier', 'A', '--host', u.hostname, '--port', u.port, '--as-host', 'localhost']);
  assert.match(stdout, /as_host=localhost/);
  assert.ok(stdout.includes(encodeURIComponent(`http://localhost:${u.port}/wallet/direct_post`)));
});

// ---- row 5: an already-registered tier-B result must not say "now registered" ----------------------------------------

test('row 5: renderOutcome words a tier-B repeat as already registered, and a first scan as now registered', async () => {
  const html = await page(false);
  const m = html.match(/function renderOutcome\([\s\S]*?\n\}\n(?=\s*function renderCompare)/);
  assert.ok(m, 'renderOutcome is on the page');
  const run = (s) => {
    const el = () => ({ hidden: true, textContent: '', className: '' });
    const e = { outcomeSection: el(), outcomeHeading: el(), outcomeLine: el(), alreadyRegistered: el(), verdictJson: el(), storeState: el() };
    new Function('outcomeSection', 'outcomeHeading', 'outcomeLine', 'alreadyRegistered', 'verdictJson', 'storeState', 'log', 'renderTierC', `${m[0]}; renderOutcome('B', ${JSON.stringify(s)});`)(
      e.outcomeSection, e.outcomeHeading, e.outcomeLine, e.alreadyRegistered, e.verdictJson, e.storeState, () => {}, () => {});
    return e;
  };
  const verdict = { ok: true, allowed: true, valid: true };
  const first = run({ verdict, already_registered: false, zktag_seen_before: false });
  assert.equal(first.outcomeHeading.textContent, 'Registered as a unique adult human');
  assert.match(first.outcomeLine.textContent, /is now registered/);
  assert.equal(first.alreadyRegistered.hidden, true);
  const again = run({ verdict, already_registered: true, zktag_seen_before: true });
  assert.match(again.outcomeHeading.textContent, /^Already registered/);
  assert.match(again.outcomeLine.textContent, /was already registered/);
  assert.doesNotMatch(again.outcomeLine.textContent, /now registered/);
  assert.equal(again.alreadyRegistered.hidden, false);
});
