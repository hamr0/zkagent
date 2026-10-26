// node --test — M3b chunk 2 (items 14 and 15, D110): the demo verifier's tier-C round trip through chiproof, against the
// REAL HTTP server on an ephemeral port, driven by a scripted "app" (not a device). Final wire: the predicates ride inside
// the signed challenge, the answer is {spec:'zkagent/1', tier:'C', challenge, answers}, a refusal is error=access_denied.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createPublicKey } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { request as httpRequest } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { startServer } from '../server.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';
import {
  isLoopbackRequest, describeVerdictForLog, adbForceStopArgs, adbLaunchArgs,
} from '../tierc.mjs';

let storeDir;
let srv;
before(async () => {
  storeDir = mkdtempSync(join(tmpdir(), 'zkagent-demo-tierc-'));
  process.env.DEMO_STORE_PATH = join(storeDir, 'store.json');
  srv = await startServer(0);
});
after(async () => {
  await srv.close();
  delete process.env.DEMO_STORE_PATH;
  rmSync(storeDir, { recursive: true, force: true });
});

async function create(body) {
  return fetch(`${srv.url}/ui/presentations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
  });
}
async function requestObject(tx) {
  const res = await fetch(tx.request_uri);
  assert.equal(res.status, 200);
  const v = verifyJws(await res.text(), createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem));
  assert.equal(v.valid, true, `request JWS must verify (got ${v.reason})`);
  return v.payload;
}
async function post(req, presentation) {
  const vp = Buffer.from(JSON.stringify(presentation)).toString('base64url');
  return fetch(req.response_uri, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: `state=${encodeURIComponent(req.state)}&vp_token=${encodeURIComponent(vp)}`,
  });
}
/** What the scanner posts for a refusal (D110): error=access_denied plus state, nothing else. */
async function postRefusal(req, extra = '') {
  return fetch(req.response_uri, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: `error=access_denied&state=${encodeURIComponent(req.state)}${extra}`,
  });
}
async function poll(tx) {
  const r = await fetch(`${srv.url}/ui/presentations/${tx.transactionId}`);
  return r.json();
}
function answerFor(req, results) {
  return {
    spec: 'zkagent/1', tier: 'C', challenge: req.zkagent.challenge,
    answers: req.zkagent.challenge.predicates.map((p, i) => {
      const a = { name: p.name };
      if (p.months !== undefined) a.months = p.months;
      if (p.age !== undefined) a.age = p.age;
      return { ...a, result: results[i] };
    }),
  };
}
const mkC = async (predicates) => { const tx = await (await create({ mode: 'C', predicates })).json(); return [tx, await requestObject(tx)]; };

// Invented values: nothing here is a real name, number or nationality.
const ALL_FIVE = [
  { name: 'expiry_beyond', months: 6 },
  { name: 'dob_at_least', age: 21 },
  { name: 'nationality_is', code: 'nld' },
  { name: 'nationality_not_in', codes: ['cub', 'rus'] },
  { name: 'name_match', given_names: 'Zqxjw Pl', surname: 'Vbnmlk' },
  { name: 'document_number_is', number: 'ZZ9988776' },
];
const SECRETS = ['Zqxjw', 'Vbnmlk', 'ZZ9988776', 'CUB', 'RUS', 'NLD'];

test('tier C: the signed request carries the tier, the predicates inside a signed challenge, and NO threshold', async () => {
  const [, req] = await mkC([{ name: 'expiry_beyond', months: 6 }]);
  assert.equal(req.zkagent.spec, 'zkagent/1');
  assert.equal(req.zkagent.tier, 'C');
  const ch = req.zkagent.challenge;
  assert.equal(ch.tier, 'C');
  assert.deepEqual(ch.predicates, [{ name: 'expiry_beyond', months: 6 }]);
  assert.equal(ch.threshold, null, 'a tier-C request must not touch the scanner\'s age lock');
  assert.equal(typeof ch.sig, 'string', 'tier-C challenges are signed (D20)');
  assert.ok(ch.expires_at > Date.now());
  assert.equal('predicates' in req.zkagent, false, 'one place for the predicates: inside the signed challenge');
  assert.equal(req.client_id, req.response_uri);
});

test('positive control: a well-formed answer is delivered and the verdict is ok:true with the booleans; the presentation is not echoed back', async () => {
  const [tx, req] = await mkC([{ name: 'expiry_beyond', months: 3 }]);
  assert.equal((await post(req, answerFor(req, [true]))).status, 200);
  const s = await poll(tx);
  assert.equal(s.status, 'done');
  assert.equal(s.verdict.ok, true);
  assert.equal(s.verdict.allowed, true);
  assert.deepEqual(s.verdict.answers, [{ name: 'expiry_beyond', months: 3, result: true }]);
  assert.equal(s.presentation, null, 'the echoed challenge would carry site-supplied values, so the poll never returns it');
});

test('a false answer is a legitimate answer, not a failure', async () => {
  const [tx, req] = await mkC([{ name: 'expiry_beyond', months: 12 }]);
  await post(req, answerFor(req, [false]));
  const s = await poll(tx);
  assert.equal(s.verdict.ok, true);
  assert.equal(s.verdict.allowed, true);
  assert.equal(s.verdict.answers[0].result, false);
});

test('D110: a generic access_denied is a distinct "refused" verdict: a real no, not a pass, not a crash, not pending', async () => {
  const [tx, req] = await mkC(ALL_FIVE);
  assert.equal((await poll(tx)).status, 'pending');
  assert.equal((await postRefusal(req)).status, 200);
  const s = await poll(tx);
  assert.equal(s.status, 'done');
  assert.deepEqual(s.verdict, { ok: true, allowed: false, reason: 'refused_by_holder', tier: 'C', refused: true });
  assert.equal(s.presentation, null);
  // the same body for every reason: an error_description changes nothing
  const [tx2, req2] = await mkC([{ name: 'expiry_beyond', months: 3 }]);
  await postRefusal(req2, '&error_description=holder_setting_off');
  assert.deepEqual((await poll(tx2)).verdict, s.verdict);
  // a second post is 409, never a second verdict
  assert.equal((await postRefusal(req)).status, 409);
  // JSON body works too
  const [tx3, req3] = await mkC([{ name: 'expiry_beyond', months: 3 }]);
  const j = await fetch(req3.response_uri, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ error: 'access_denied', state: req3.state }) });
  assert.equal(j.status, 200);
  assert.equal((await poll(tx3)).verdict.refused, true);
});

test('D110: other error values are "could not check" (ok:false, allowed:null), never refused and never a pass', async () => {
  for (const err of ['server_error', 'invalid_request', 'ACCESS_DENIED', '']) {
    const [tx, req] = await mkC([{ name: 'expiry_beyond', months: 3 }]);
    const res = await fetch(req.response_uri, {
      method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: `error=${encodeURIComponent(err)}&state=${encodeURIComponent(req.state)}`,
    });
    assert.equal(res.status, 200, err);
    const v = (await poll(tx)).verdict;
    assert.equal(v.ok, false, err); assert.equal(v.allowed, null, err); assert.equal(v.refused, undefined, err);
  }
});

test('an error response for a tier A or B transaction is still the 400 it always was (D110 is tier C only)', async () => {
  for (const mode of ['A', 'B']) {
    const tx = await (await create({ mode })).json();
    const req = await requestObject(tx);
    assert.equal((await postRefusal(req)).status, 400, mode);
    assert.equal((await poll(tx)).status, 'pending', mode);
  }
});

test('nothing posted means "pending", then "expired" after the lifetime: "no answer", distinct from a refusal', async () => {
  const tx = await (await create({ mode: 'C', ttlMs: 1 })).json();
  await new Promise((r) => setTimeout(r, 15));
  const s = await poll(tx);
  assert.deepEqual(s, { status: 'expired' });
  const live = await (await create({ mode: 'C' })).json();
  assert.equal((await poll(live)).status, 'pending'); // paired control: not expired while live
  const a = await (await create({ mode: 'A', ttlMs: 1 })).json();
  await new Promise((r) => setTimeout(r, 15));
  assert.equal((await poll(a)).status, 'pending', 'tier A is unchanged');
});

test('negatives, each paired with the passing control above: wrong shape, wrong answer set, non-boolean, replay', async () => {
  const cases = [];
  { const [tx, req] = await mkC(); cases.push([tx, req, { ...answerFor(req, [true]), spec: 'zkagent-poc-tierc/0' }, 'unsupported_spec']); }
  { const [tx, req] = await mkC(); cases.push([tx, req, { ...answerFor(req, [true]), tier: 'B' }, 'tier_invalid']); }
  { const [tx, req] = await mkC(); cases.push([tx, req, { ...answerFor(req, [true]), challenge: { nonce: 'not-this-transactions' } }, 'state_challenge_mismatch']); }
  { const [tx, req] = await mkC([{ name: 'expiry_beyond', months: 3 }]); const p = answerFor(req, [true]); p.answers[0].months = 6;
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  { const [tx, req] = await mkC(); const p = answerFor(req, [true]); p.answers.push({ name: 'expiry_beyond', months: 12, result: true });
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  { const [tx, req] = await mkC(); cases.push([tx, req, answerFor(req, ['true']), 'answer_not_boolean']); }
  // the challenge's predicates edited by the "app": the signature breaks
  { const [tx, req] = await mkC([{ name: 'expiry_beyond', months: 3 }]); const p = answerFor(req, [true]);
    p.challenge = { ...p.challenge, predicates: [{ name: 'expiry_beyond', months: 12 }] }; p.answers[0].months = 12;
    cases.push([tx, req, p, 'signature_invalid']); }
  assert.ok(cases.length > 0);
  for (const [tx, req, presentation, reason] of cases) {
    await post(req, presentation);
    const s = await poll(tx);
    assert.equal(s.status, 'done');
    assert.equal(s.verdict.ok, true, `${reason}: a real no is ok:true`);
    assert.equal(s.verdict.allowed, false, `${reason}: must NOT be allowed`);
    assert.equal(s.verdict.reason, reason);
  }
});

test('a second answer to the same transaction is HTTP 409, never a second verdict (explicit refusal, not success)', async () => {
  const [tx, req] = await mkC();
  assert.equal((await post(req, answerFor(req, [true]))).status, 200);
  assert.equal((await post(req, answerFor(req, [false]))).status, 409);
  assert.equal((await poll(tx)).verdict.answers[0].result, true, 'the first answer stands');
});

test('bad predicate requests are a 400 and make no transaction', async () => {
  for (const predicates of [[], 'x', [{ name: 'name_match', months: 3 }], [{ name: 'expiry_beyond', months: 4 }], [{ name: 'expiry_beyond', months: '3' }], [null]]) {
    const res = await create({ mode: 'C', predicates });
    assert.equal(res.status, 400, JSON.stringify(predicates));
  }
  assert.equal((await create({ mode: 'C', predicates: [{ name: 'expiry_beyond', months: 12 }] })).status, 201); // control
});

test('tier A and B requests are unchanged by the tier-C addition', async () => {
  const a = await (await create({ mode: 'A' })).json();
  const reqA = await requestObject(a);
  assert.equal(reqA.zkagent.tier, 'A');
  assert.equal(reqA.zkagent.challenge.threshold, 18);
  assert.equal('predicates' in reqA.zkagent.challenge, false);
  const b = await (await create({ mode: 'B' })).json();
  assert.equal((await requestObject(b)).zkagent.tier, 'B');
});

// ---------------------------------------------------------------- all five kinds, per predicate, in order

test('one request carries all five predicate kinds (six predicates), signed, and the answer comes back per predicate in order', async () => {
  const [tx, req] = await mkC(ALL_FIVE);
  const preds = req.zkagent.challenge.predicates;
  assert.deepEqual(preds.map((p) => p.name),
    ['expiry_beyond', 'dob_at_least', 'nationality_is', 'nationality_not_in', 'name_match', 'document_number_is']);
  assert.deepEqual(preds[2], { name: 'nationality_is', code: 'NLD' }, 'codes are upper-cased');
  assert.deepEqual(preds[4], { name: 'name_match', given_names: 'Zqxjw Pl', surname: 'Vbnmlk' }, 'two labelled fields');
  assert.equal((await post(req, answerFor(req, [true, false, true, true, true, false]))).status, 200);
  const s = await poll(tx);
  assert.equal(s.verdict.ok, true);
  assert.equal(s.verdict.allowed, true);
  assert.deepEqual(s.verdict.answers, [
    { name: 'expiry_beyond', months: 6, result: true },
    { name: 'dob_at_least', age: 21, result: false },
    { name: 'nationality_is', result: true },
    { name: 'nationality_not_in', result: true },
    { name: 'name_match', result: true },
    { name: 'document_number_is', result: false },
  ]);
  const kept = JSON.stringify(s);
  for (const secret of SECRETS) assert.equal(kept.includes(secret), false, secret);
});

test('negatives for the kinds: a wrong boundary, a wrong order, a missing answer and a smuggled value are real noes', async () => {
  const cases = [];
  { const [tx, req] = await mkC(ALL_FIVE); const p = answerFor(req, [true, true, true, true, true, true]); p.answers[1].age = 18; cases.push([tx, req, p]); }
  { const [tx, req] = await mkC(ALL_FIVE); const p = answerFor(req, [true, true, true, true, true, true]); [p.answers[2], p.answers[3]] = [p.answers[3], p.answers[2]]; cases.push([tx, req, p]); }
  { const [tx, req] = await mkC(ALL_FIVE); const p = answerFor(req, [true, true, true, true, true, true]); p.answers.pop(); cases.push([tx, req, p]); }
  { const [tx, req] = await mkC(ALL_FIVE); const p = answerFor(req, [true, true, true, true, true, true]); p.answers[4].value = 'Vbnmlk'; cases.push([tx, req, p]); }
  assert.ok(cases.length > 0);
  for (const [tx, req, presentation] of cases) {
    await post(req, presentation);
    const s = await poll(tx);
    assert.equal(s.verdict.ok, true);
    assert.equal(s.verdict.allowed, false);
    assert.equal(s.verdict.reason, 'answers_do_not_match_request');
  }
  const [tx, req] = await mkC(ALL_FIVE); // paired pass
  await post(req, answerFor(req, [true, true, true, true, true, true]));
  assert.equal((await poll(tx)).verdict.allowed, true);
});

// ---------------------------------------------------------------- loopback only, logging, page

test('D109(g)8: the loopback gate needs BOTH a loopback Host and a loopback peer; a forged Host from a non-loopback peer is refused', () => {
  assert.equal(isLoopbackRequest('127.0.0.1:8787', '127.0.0.1'), true);
  assert.equal(isLoopbackRequest('localhost:8787', '::1'), true);
  assert.equal(isLoopbackRequest('[::1]:8787', '::ffff:127.0.0.1'), true);
  assert.equal(isLoopbackRequest('192.168.1.5:8787', '127.0.0.1'), false, 'LAN Host name');
  assert.equal(isLoopbackRequest('127.0.0.1:8787', '192.168.1.9'), false, 'forged Host from a LAN peer');
  assert.equal(isLoopbackRequest('evil.test', '127.0.0.1'), false);
  assert.equal(isLoopbackRequest(undefined, '127.0.0.1'), false);
  assert.equal(isLoopbackRequest('127.0.0.1', undefined), false);
  assert.equal(isLoopbackRequest('::::', '127.0.0.1'), false);
});

test('D109(g)8 on the real server: tier C with a non-loopback Host header is a 403 and makes no transaction; tier A is unaffected', async () => {
  const send = (host, mode) => new Promise((resolve, reject) => {
    const body = JSON.stringify({ mode });
    const r = httpRequest({ host: '127.0.0.1', port: srv.port, path: '/ui/presentations', method: 'POST', headers: { host, 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) } },
      (res) => { let t = ''; res.on('data', (c) => { t += c; }); res.on('end', () => resolve({ status: res.statusCode, body: t })); });
    r.on('error', reject); r.end(body);
  });
  const c = await send('192.168.1.5:8787', 'C');
  assert.equal(c.status, 403);
  assert.equal(JSON.parse(c.body).error, 'tier_c_loopback_only');
  assert.equal((await send('192.168.1.5:8787', 'A')).status, 201, 'tier A is not loopback-gated');
  assert.equal((await send(`127.0.0.1:${srv.port}`, 'C')).status, 201, 'paired control: loopback is accepted');
});

test('logging: the verifier logs predicate names, boundaries, booleans and the refusal marker only', async () => {
  const lines = [];
  const orig = console.log;
  console.log = (...a) => { lines.push(a.join(' ')); };
  try {
    const [, req] = await mkC(ALL_FIVE);
    await post(req, answerFor(req, [true, true, true, true, true, true]));
    const [, req2] = await mkC(ALL_FIVE);
    await postRefusal(req2);
  } finally { console.log = orig; }
  const all = lines.join('\n');
  assert.match(all, /tx created transactionId=\S+ mode=C ttlMs=\d+ predicates=expiry_beyond:6,dob_at_least:21,nationality_is,nationality_not_in,name_match,document_number_is/);
  assert.match(all, /tier=C ok=true allowed=true reason=answers-received answered=expiry_beyond:6=true,dob_at_least:21=true,nationality_is=true/);
  assert.match(all, /tier=C ok=true allowed=false reason=refused_by_holder refused=true/);
  for (const secret of SECRETS) assert.equal(all.includes(secret), false, secret);
  assert.equal(all.includes('nonce'), false, 'no nonce in logs');
  assert.equal(describeVerdictForLog({ answers: [{ name: 'expiry_beyond', months: 3, result: false }] }), 'answered=expiry_beyond:3=false');
  assert.equal(describeVerdictForLog({ refused: true }), 'refused=true');
  assert.equal(describeVerdictForLog(null), 'answered=none');
  assert.ok(lines.length >= 4, 'non-vacuity: the test captured the server\'s log output');
});

test('the page offers tier C on loopback only, with two labelled name fields, one generic Refused outcome, and no value rendering', async () => {
  const html = await (await fetch(`${srv.url}/`)).text();
  assert.match(html, /id="goC" hidden/, 'hidden until the script confirms a loopback hostname');
  assert.match(html, /\['127\.0\.0\.1', 'localhost', '\[::1\]'\]\.includes\(location\.hostname\)/);
  assert.match(html, /for="cGiven">Given names/);
  assert.match(html, /for="cSurname">Surname/);
  assert.equal((html.match(/id="cGiven"/g) ?? []).length, 1);
  assert.match(html, /outcomeHeading\.textContent = 'Refused'/);
  assert.match(html, /outcomeHeading\.textContent = 'No answer'/);
  assert.match(html, /#outcomeHeading\.refused/);
  // the page renders names and booleans from the verdict only: it never reads a presentation or the typed values back
  const tierCSection = html.slice(html.indexOf('function renderTierC'), html.indexOf('function renderOutcome'));
  assert.ok(tierCSection.length > 200, 'non-vacuity: found the tier-C render function');
  for (const forbidden of ['presentation', 'given_names', 'surname', 'cGiven', 'cNumber', 'predicates']) assert.equal(tierCSection.includes(forbidden), false, forbidden);
});

// ---------------------------------------------------------------- the CLI helper

test('adb launch arguments keep the whole av:// link single-quoted, so the & survives the device shell', async () => {
  const [tx] = await mkC();
  assert.ok(tx.app_link_av.includes('&'), 'precondition: the real link contains an &');
  const args = adbLaunchArgs(tx.app_link_av);
  assert.deepEqual(args.slice(0, 6), ['shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d']);
  assert.equal(args[6], `'${tx.app_link_av}'`);
  assert.deepEqual(args.slice(7), ['-p', 'com.zkagent.scanner']);
  assert.deepEqual(adbForceStopArgs(), ['shell', 'am', 'force-stop', 'com.zkagent.scanner']);
});

const SCRIPT = fileURLToPath(new URL('../scripts/tierc-poc-request.mjs', import.meta.url));
const run = (args, env = {}) => promisify(execFile)(process.execPath, [SCRIPT, ...args], { env: { ...process.env, ...env }, encoding: 'utf8' });

test('the CLI helper --launch runs force-stop THEN the quoted launch, and --wait reports a pending transaction', async () => {
  const u = new URL(srv.url);
  // ADB=echo: nothing touches a device. ASYNC on purpose: the server under test lives in THIS process.
  const { stdout: out } = await run(['--tier', 'C', '--host', u.hostname, '--port', u.port, '--months', '6', '--launch', '--wait', '1'], { ADB: 'echo' });
  const lines = out.split('\n');
  const stop = lines.findIndex((l) => l.startsWith('$ echo shell am force-stop'));
  const start = lines.findIndex((l) => l.startsWith('$ echo shell am start -a android.intent.action.VIEW -d \'av://authorize?'));
  assert.ok(stop >= 0 && start > stop, `force-stop must precede the launch:\n${out}`);
  assert.ok(lines[start].endsWith("' -p com.zkagent.scanner"));
  assert.match(out, /RESULT status=pending/);
  assert.doesNotMatch(out, /expiry_beyond:\d+=(true|false)/, 'nothing was answered, so no answer may be printed');
});

test('the CLI helper builds the five kinds from flags and --ttl reaches the verifier; it never prints a value', async () => {
  const u = new URL(srv.url);
  const seen = [];
  const orig = console.log;
  console.log = (...a) => { seen.push(a.join(' ')); };
  let out;
  try {
    ({ stdout: out } = await run(['--tier', 'C', '--host', u.hostname, '--port', u.port,
      '--months', '6', '--age', '21', '--nationality-is', 'nld', '--nationality-not-in', 'cub,rus', '--ttl', '300'],
    { TIERC_GIVEN_NAMES: 'Zqxjw Pl', TIERC_SURNAME: 'Vbnmlk', TIERC_DOC_NUMBER: 'ZZ9988776' }));
  } finally { console.log = orig; }
  assert.ok(seen.some((l) => l.includes('mode=C ttlMs=300000')), 'the 300 s lifetime reached the verifier');
  assert.match(out, /asks=expiry_beyond:6,dob_at_least:21,nationality_is,nationality_not_in,name_match,document_number_is ttl_s=300/);
  for (const secret of [...SECRETS, 'nld', 'cub']) assert.equal(out.includes(secret), false, secret);
  await assert.rejects(run(['--tier', 'C', '--host', u.hostname, '--port', u.port, '--given-names', 'x']));
});

test('the CLI helper reports a refusal and an expiry distinctly from a pending request', async () => {
  const u = new URL(srv.url);
  // Refused: the helper creates the transaction and polls; this scripted "app" reads the link the helper printed and refuses it.
  const child = spawn(process.execPath, [SCRIPT, '--tier', 'C', '--host', u.hostname, '--port', u.port, '--months', '3', '--wait', '15']);
  let out = '';
  let refused = false;
  const done = new Promise((resolve) => child.on('close', resolve));
  child.stdout.on('data', async (c) => {
    out += c;
    const m = /^'(av:\/\/authorize\?[^']+)'$/m.exec(out);
    if (m && !refused) {
      refused = true;
      const requestUri = new URL(m[1]).searchParams.get('request_uri');
      const res = await fetch(requestUri);
      const payload = verifyJws(await res.text(), createPublicKey(DEV_REQUEST_SIGNER.publicKeyPem)).payload;
      await postRefusal(payload);
    }
  });
  await done;
  assert.match(out, /RESULT status=done ok=true allowed=false reason=refused_by_holder answered=none refused=true/);
  // Expired: nothing is ever posted
  const expired = await run(['--tier', 'C', '--host', u.hostname, '--port', u.port, '--months', '3', '--ttl', '1', '--wait', '8']);
  assert.match(expired.stdout, /RESULT status=expired/);
  assert.doesNotMatch(expired.stdout, /refused=true/);
});

test('raw-predicates device-test hook: ignored unless TIERC_POC_ALLOW_RAW=1, and then signed exactly as given', async () => {
  const offList = [{ name: 'expiry_beyond', months: 6 }, { name: 'dob_at_least', age: 19 }];
  const off = await (await create({ mode: 'C', rawPredicates: offList })).json();
  assert.deepEqual((await requestObject(off)).zkagent.challenge.predicates, [{ name: 'expiry_beyond', months: 3 }]);
  assert.equal((await create({ mode: 'C', predicates: offList })).status, 400);
  process.env.TIERC_POC_ALLOW_RAW = '1';
  try {
    const on = await (await create({ mode: 'C', rawPredicates: offList })).json();
    const ch = (await requestObject(on)).zkagent.challenge;
    assert.deepEqual(ch.predicates, offList);
    assert.equal(typeof ch.sig, 'string');
  } finally { delete process.env.TIERC_POC_ALLOW_RAW; }
});
