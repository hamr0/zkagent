// node --test — M3b opening POC (§6.9.1 criteria 3-4): the demo verifier's tier-C round trip, against the REAL HTTP
// server on an ephemeral port. POC-local wire (zkagent-poc-tierc/0), NOT chiproof.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createPublicKey } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { startServer } from '../server.mjs';
import { verifyJws } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';
import {
  validatePredicates, verifyTierCPoc, issueTierCChallenge, describeVerdictForLog, POC_SPEC,
  adbForceStopArgs, adbLaunchArgs,
} from '../tierc-poc.mjs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

let storeDir;
let srv;
before(async () => {
  storeDir = mkdtempSync(join(tmpdir(), 'zkagent-demo-tierc-poc-'));
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
async function poll(tx) {
  const r = await fetch(`${srv.url}/ui/presentations/${tx.transactionId}`);
  return r.json();
}
function answerFor(req, results) {
  return {
    spec: POC_SPEC, tier: 'C', challenge: req.zkagent.challenge,
    answers: req.zkagent.predicates.map((p, i) => {
      const a = { name: p.name };
      if (p.months !== undefined) a.months = p.months;
      if (p.age !== undefined) a.age = p.age;
      return { ...a, result: results[i] };
    }),
  };
}

test('tier C: the signed request carries the tier, the predicate, an expiring challenge and NO threshold', async () => {
  const res = await create({ mode: 'C', predicates: [{ name: 'expiry_beyond', months: 6 }] });
  assert.equal(res.status, 201);
  const tx = await res.json();
  const req = await requestObject(tx);
  assert.equal(req.zkagent.tier, 'C');
  assert.deepEqual(req.zkagent.predicates, [{ name: 'expiry_beyond', months: 6 }]);
  assert.ok(req.zkagent.challenge.nonce.length > 10);
  assert.ok(req.zkagent.challenge.expires_at > Date.now());
  assert.equal('threshold' in req.zkagent.challenge, false, 'a tier-C request must not touch the scanner\'s age lock');
  assert.equal(req.client_id, req.response_uri);
});

test('positive control: a well-formed answer is delivered and the verdict is ok:true with the booleans', async () => {
  const tx = await (await create({ mode: 'C', predicates: [{ name: 'expiry_beyond', months: 3 }] })).json();
  const req = await requestObject(tx);
  const res = await post(req, answerFor(req, [true]));
  assert.equal(res.status, 200);
  const s = await poll(tx);
  assert.equal(s.status, 'done');
  assert.equal(s.verdict.ok, true);
  assert.equal(s.verdict.allowed, true);
  assert.deepEqual(s.verdict.answers, [{ name: 'expiry_beyond', months: 3, result: true }]);
  assert.notEqual(s.presentation, null);
});

test('a false answer is a legitimate answer, not a failure', async () => {
  const tx = await (await create({ mode: 'C', predicates: [{ name: 'expiry_beyond', months: 12 }] })).json();
  const req = await requestObject(tx);
  await post(req, answerFor(req, [false]));
  const s = await poll(tx);
  assert.equal(s.verdict.ok, true);
  assert.equal(s.verdict.allowed, true);
  assert.equal(s.verdict.answers[0].result, false);
});

test('refused in the app means nothing arrives: the transaction stays pending (the verifier sees nothing)', async () => {
  const tx = await (await create({ mode: 'C' })).json();
  await requestObject(tx); // the app fetched the request, then refused before any read or mint
  const s = await poll(tx);
  assert.equal(s.status, 'pending');
  assert.equal('verdict' in s, false);
});

test('negatives, each paired with the passing control above: wrong shape, wrong answer set, non-boolean, replay', async () => {
  const cases = [];
  // wrong spec
  { const tx = await (await create({ mode: 'C' })).json(); const req = await requestObject(tx);
    cases.push([tx, req, { ...answerFor(req, [true]), spec: 'zkagent/1' }, 'unsupported_spec']); }
  // tier B presentation to a tier C transaction
  { const tx = await (await create({ mode: 'C' })).json(); const req = await requestObject(tx);
    cases.push([tx, req, { ...answerFor(req, [true]), tier: 'B' }, 'tier_mismatch']); }
  // someone else's nonce
  { const tx = await (await create({ mode: 'C' })).json(); const req = await requestObject(tx);
    cases.push([tx, req, { ...answerFor(req, [true]), challenge: { nonce: 'not-this-transactions' } }, 'state_challenge_mismatch']); }
  // answers for a boundary that was not asked
  { const tx = await (await create({ mode: 'C', predicates: [{ name: 'expiry_beyond', months: 3 }] })).json(); const req = await requestObject(tx);
    const p = answerFor(req, [true]); p.answers[0].months = 6;
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  // an extra, unasked answer
  { const tx = await (await create({ mode: 'C' })).json(); const req = await requestObject(tx);
    const p = answerFor(req, [true]); p.answers.push({ name: 'expiry_beyond', months: 12, result: true });
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  // a non-boolean result (a string "true" must not pass as true)
  { const tx = await (await create({ mode: 'C' })).json(); const req = await requestObject(tx);
    const p = answerFor(req, ['true']);
    cases.push([tx, req, p, 'answer_not_boolean']); }
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
  const tx = await (await create({ mode: 'C' })).json();
  const req = await requestObject(tx);
  assert.equal((await post(req, answerFor(req, [true]))).status, 200);
  const again = await post(req, answerFor(req, [false]));
  assert.equal(again.status, 409);
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
  assert.equal('predicates' in reqA.zkagent, false);
  const b = await (await create({ mode: 'B' })).json();
  assert.equal((await requestObject(b)).zkagent.tier, 'B');
});

test('verdict invariant: ok:false forces allowed:null, and nothing throws on garbage', () => {
  const tx = { challenge: issueTierCChallenge({ ttlMs: 1000 }), predicates: [{ name: 'expiry_beyond', months: 3 }] };
  for (const junk of [null, undefined, 42, 'x', [], {}, { spec: POC_SPEC }, { spec: POC_SPEC, tier: 'C', challenge: null }]) {
    const v = verifyTierCPoc(junk, tx);
    if (v.ok === false) assert.equal(v.allowed, null);
    else assert.equal(v.allowed, false);
  }
  const bad = verifyTierCPoc({ spec: POC_SPEC, tier: 'C', challenge: tx.challenge, answers: [] }, tx, Number.NaN);
  assert.deepEqual(bad, { ok: false, allowed: null, reason: 'clock_unavailable' });
  // an expired challenge is a real no
  const expired = verifyTierCPoc({ spec: POC_SPEC, tier: 'C', challenge: tx.challenge, answers: [{ name: 'expiry_beyond', months: 3, result: true }] }, tx, tx.challenge.expires_at + 1);
  assert.deepEqual(expired, { ok: true, allowed: false, reason: 'challenge_expired' });
  assert.deepEqual(validatePredicates(undefined), { ok: true, predicates: [{ name: 'expiry_beyond', months: 3 }] });
});

test('logging: the verifier logs predicate names, boundaries and booleans only', async () => {
  const lines = [];
  const orig = console.log;
  console.log = (...a) => { lines.push(a.join(' ')); };
  try {
    const tx = await (await create({ mode: 'C', predicates: [{ name: 'expiry_beyond', months: 6 }] })).json();
    const req = await requestObject(tx);
    await post(req, answerFor(req, [true]));
  } finally { console.log = orig; }
  const verdictLine = lines.find((l) => l.includes('verdict transactionId='));
  assert.ok(verdictLine, 'a verdict line must be logged');
  assert.match(verdictLine, /tier=C ok=true allowed=true reason=answers-received answered=expiry_beyond:6=true/);
  assert.equal(describeVerdictForLog({ answers: [{ name: 'expiry_beyond', months: 3, result: false }] }), 'answered=expiry_beyond:3=false');
  const all = lines.join('\n');
  assert.equal(all.includes('nonce'), false, 'no nonce in logs');
  assert.ok(lines.length >= 2, 'non-vacuity: the test captured the server\'s log output');
});

test('adb launch arguments keep the whole av:// link single-quoted, so the & survives the device shell', async () => {
  const tx = await (await create({ mode: 'C' })).json();
  assert.ok(tx.app_link_av.includes('&'), 'precondition: the real link contains an &');
  const args = adbLaunchArgs(tx.app_link_av);
  assert.deepEqual(args.slice(0, 6), ['shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d']);
  assert.equal(args[6], `'${tx.app_link_av}'`);
  assert.deepEqual(args.slice(7), ['-p', 'com.zkagent.scanner']);
  assert.deepEqual(adbForceStopArgs(), ['shell', 'am', 'force-stop', 'com.zkagent.scanner']);
});

test('the CLI helper --launch runs force-stop THEN the quoted launch, and --wait reports a pending transaction', async () => {
  const script = fileURLToPath(new URL('../scripts/tierc-poc-request.mjs', import.meta.url));
  const u = new URL(srv.url);
  // ADB=echo: nothing touches a device; the printed commands are what adb would have received.
  // ASYNC on purpose: the server under test lives in THIS process, so a blocking exec would starve it and deadlock.
  const { stdout: out } = await promisify(execFile)(process.execPath, [script, '--tier', 'C', '--host', u.hostname, '--port', u.port, '--months', '6', '--launch', '--wait', '1'],
    { env: { ...process.env, ADB: 'echo' }, encoding: 'utf8' });
  const lines = out.split('\n');
  const stop = lines.findIndex((l) => l.startsWith('$ echo shell am force-stop'));
  const start = lines.findIndex((l) => l.startsWith('$ echo shell am start -a android.intent.action.VIEW -d \'av://authorize?'));
  assert.ok(stop >= 0 && start > stop, `force-stop must precede the launch:\n${out}`);
  assert.ok(lines[start].endsWith("' -p com.zkagent.scanner"));
  assert.match(out, /RESULT status=pending/);
  assert.doesNotMatch(out, /expiry_beyond:\d+=(true|false)/, 'nothing was answered, so no answer may be printed');
});

// ---------------------------------------------------------------- chunk 1: all five predicate kinds in one request

const ALL_FIVE = [
  { name: 'expiry_beyond', months: 6 },
  { name: 'dob_at_least', age: 21 },
  { name: 'nationality_is', code: 'nld' },
  { name: 'nationality_not_in', codes: ['cub', 'rus'] },
  { name: 'name_match', given_names: 'Zqxjw Pl', surname: 'Vbnmlk' },
  { name: 'document_number_is', number: 'ZZ9988776' },
];

test('one request carries all five predicate kinds (six predicates), signed, and the answer comes back per predicate in order', async () => {
  const res = await create({ mode: 'C', predicates: ALL_FIVE });
  assert.equal(res.status, 201);
  const tx = await res.json();
  const req = await requestObject(tx);
  assert.deepEqual(req.zkagent.predicates.map((p) => p.name),
    ['expiry_beyond', 'dob_at_least', 'nationality_is', 'nationality_not_in', 'name_match', 'document_number_is']);
  assert.deepEqual(req.zkagent.predicates[2], { name: 'nationality_is', code: 'NLD' }, 'codes are upper-cased');
  assert.deepEqual(req.zkagent.predicates[4], { name: 'name_match', given_names: 'Zqxjw Pl', surname: 'Vbnmlk' }, 'two labelled fields');
  assert.equal('threshold' in req.zkagent.challenge, false);
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
  // the verdict the verifier keeps carries no site-supplied value
  const kept = JSON.stringify(s.verdict);
  for (const secret of ['Zqxjw', 'Vbnmlk', 'ZZ9988776', 'CUB', 'RUS', 'NLD']) assert.equal(kept.includes(secret), false, secret);
});

test('validatePredicates: every kind validates, and each bad shape is refused beside the passing control', () => {
  assert.equal(validatePredicates(ALL_FIVE).ok, true); // control
  const bad = [
    [{ name: 'dob_at_least', age: 19 }], [{ name: 'dob_at_least', age: '18' }], [{ name: 'expiry_beyond', months: 24 }],
    [{ name: 'nationality_is', code: 'N1D' }], [{ name: 'nationality_is', code: 'NLDX' }], [{ name: 'nationality_not_in', codes: [] }],
    [{ name: 'nationality_not_in', codes: Array.from({ length: 11 }, () => 'CUB') }],
    [{ name: 'nationality_not_in', codes: ['CUB', 5] }],
    [{ name: 'name_match', given_names: 'Jan' }], [{ name: 'name_match', surname: 'Vries' }], [{ name: 'name_match', given_names: ' ', surname: 'x' }],
    [{ name: 'document_number_is', number: '' }], [{ name: 'document_number_is', number: '<<<' }], [{ name: 'shoe_size' }],
  ];
  let refused = 0;
  for (const b of bad) { assert.equal(validatePredicates(b).ok, false, JSON.stringify(b)); refused += 1; }
  assert.equal(refused, bad.length);
  assert.ok(refused > 0);
  assert.equal(validatePredicates([{ name: 'nationality_not_in', codes: Array.from({ length: 10 }, () => 'CUB') }]).ok, true); // cap is 10
  assert.equal(validatePredicates([{ name: 'nationality_is', code: 'D' }]).ok, true, 'ICAO special code is valid');
});

test('negatives for the new kinds: a wrong boundary, a wrong order and a missing answer are real noes', async () => {
  const mk = async () => { const tx = await (await create({ mode: 'C', predicates: ALL_FIVE })).json(); return [tx, await requestObject(tx)]; };
  const cases = [];
  { const [tx, req] = await mk(); const p = answerFor(req, [true, true, true, true, true, true]); p.answers[1].age = 18;
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  { const [tx, req] = await mk(); const p = answerFor(req, [true, true, true, true, true, true]); [p.answers[2], p.answers[3]] = [p.answers[3], p.answers[2]];
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  { const [tx, req] = await mk(); const p = answerFor(req, [true, true, true, true, true, true]); p.answers.pop();
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  { const [tx, req] = await mk(); const p = answerFor(req, [true, true, true, true, true, true]); p.answers[0].age = 18; // a boundary of the wrong kind
    cases.push([tx, req, p, 'answers_do_not_match_request']); }
  assert.ok(cases.length > 0);
  for (const [tx, req, presentation, reason] of cases) {
    await post(req, presentation);
    const s = await poll(tx);
    assert.equal(s.verdict.ok, true);
    assert.equal(s.verdict.allowed, false, reason);
    assert.equal(s.verdict.reason, reason);
  }
  const [tx, req] = await mk(); // paired pass
  await post(req, answerFor(req, [true, true, true, true, true, true]));
  assert.equal((await poll(tx)).verdict.allowed, true);
});

test('logging: no name, number or code is ever logged by the verifier for an all-five request', async () => {
  const lines = [];
  const orig = console.log;
  console.log = (...a) => { lines.push(a.join(' ')); };
  try {
    const tx = await (await create({ mode: 'C', predicates: ALL_FIVE })).json();
    const req = await requestObject(tx);
    await post(req, answerFor(req, [true, true, true, true, true, true]));
  } finally { console.log = orig; }
  const all = lines.join('\n');
  assert.match(all, /tx created transactionId=\S+ mode=C ttlMs=\d+ predicates=expiry_beyond:6,dob_at_least:21,nationality_is,nationality_not_in,name_match,document_number_is/);
  assert.match(all, /answered=expiry_beyond:6=true,dob_at_least:21=true,nationality_is=true/);
  for (const secret of ['Zqxjw', 'Vbnmlk', 'ZZ9988776', 'CUB', 'RUS', 'NLD']) assert.equal(all.includes(secret), false, secret);
  assert.ok(lines.length >= 2);
});

test('the CLI helper builds the five kinds from flags and --ttl reaches the verifier; it never prints a value', async () => {
  const script = fileURLToPath(new URL('../scripts/tierc-poc-request.mjs', import.meta.url));
  const u = new URL(srv.url);
  const seen = [];
  const orig = console.log;
  console.log = (...a) => { seen.push(a.join(' ')); };
  let out;
  try {
    ({ stdout: out } = await promisify(execFile)(process.execPath, [script, '--tier', 'C', '--host', u.hostname, '--port', u.port,
      '--months', '6', '--age', '21', '--nationality-is', 'nld', '--nationality-not-in', 'cub,rus', '--ttl', '300'],
    { env: { ...process.env, TIERC_GIVEN_NAMES: 'Zqxjw Pl', TIERC_SURNAME: 'Vbnmlk', TIERC_DOC_NUMBER: 'ZZ9988776' }, encoding: 'utf8' }));
  } finally { console.log = orig; }
  assert.ok(seen.some((l) => l.includes('mode=C ttlMs=300000')), 'the 300 s lifetime reached the verifier');
  assert.match(out, /asks=expiry_beyond:6,dob_at_least:21,nationality_is,nationality_not_in,name_match,document_number_is ttl_s=300/);
  for (const secret of ['Zqxjw', 'Vbnmlk', 'ZZ9988776', 'CUB', 'RUS', 'NLD', 'nld', 'cub']) assert.equal(out.includes(secret), false, secret);
  // the transaction really was made with a 300 s lifetime
  const txId = /transaction=(\S+)/.exec(out)[1];
  assert.ok(txId.length > 5);
  // a lone --given-names without --surname is a usage error, not a half request
  await assert.rejects(promisify(execFile)(process.execPath, [script, '--tier', 'C', '--host', u.hostname, '--port', u.port, '--given-names', 'x']));
});

test('POC raw-predicates hook: ignored unless TIERC_POC_ALLOW_RAW=1, and then signed exactly as given', async () => {
  const offList = [{ name: 'expiry_beyond', months: 6 }, { name: 'dob_at_least', age: 19 }];
  // off: rawPredicates is ignored, the (absent) validated predicates fall back to the default, and the off-list one is NOT signed
  const off = await (await create({ mode: 'C', rawPredicates: offList })).json();
  assert.deepEqual((await requestObject(off)).zkagent.predicates, [{ name: 'expiry_beyond', months: 3 }]);
  // the validated path still refuses the same list when it is sent as `predicates`
  assert.equal((await create({ mode: 'C', predicates: offList })).status, 400);
  process.env.TIERC_POC_ALLOW_RAW = '1';
  try {
    const on = await (await create({ mode: 'C', rawPredicates: offList })).json();
    assert.deepEqual((await requestObject(on)).zkagent.predicates, offList);
  } finally { delete process.env.TIERC_POC_ALLOW_RAW; }
});
