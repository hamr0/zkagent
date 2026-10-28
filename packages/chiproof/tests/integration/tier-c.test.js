// Tier C ("prove specific attributes", milestones.md §6.9.2 item 14, D110): predicate validation against the fixed
// vocabulary and boundary lists, challenge issuance, answers verification, and the generic-refusal verdict.
import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomBytes, sign as edSign } from 'node:crypto';
import {
  createVerifier, issueChallenge, InMemoryNonceStore, validateTierCPredicates, checkTierCAnswers, verifyTierCRefusal,
  tierCPredicateLabel, TIER_C_EXPIRY_MONTHS, TIER_C_DOB_AGES, TIER_C_MAX_EXCLUSION_SET,
} from '../../src/index.js';
import { canonicalize } from '../../src/canonical.js';

const T0 = 1_000_000;
const ISSUER = generateKeyPairSync('ed25519');
const SECRET = randomBytes(32);
// Invented values only: these tests never see a real name, number or country of a person.
const NAME = { name: 'name_match', given_names: 'Testa Mina', surname: 'Examplesson' };
const NUMBER = { name: 'document_number_is', number: 'XX0000000' };
const ALL_FIVE = [
  { name: 'expiry_beyond', months: 6 },
  { name: 'dob_at_least', age: 21 },
  { name: 'nationality_is', code: 'nld' },
  { name: 'nationality_not_in', codes: ['cub', 'RUS'] },
  NAME,
  NUMBER,
];

function makeVerifier(extra = {}) {
  return createVerifier({
    scopeDomain: 'example.test',
    challengeSecret: SECRET,
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
    allowInMemoryStore: true,
    tiers: { max: 'C' },
    trustedChallengeIssuers: [{ pubkey: ISSUER.publicKey, key_id: 'iss', maxTier: 'C' }],
    ...extra,
  });
}
const issuer = { privateKey: ISSUER.privateKey, key_id: 'iss' };
const issue = (v, predicates, opts = {}) => v.issueChallenge({ tier: 'C', predicates, ttlMs: 60_000, now: T0, issuer, ...opts });
const answersFor = (predicates, result = true) => predicates.map((p) => {
  const o = { name: p.name };
  if (p.name === 'expiry_beyond') o.months = p.months;
  if (p.name === 'dob_at_least') o.age = p.age;
  o.result = result;
  return o;
});
const present = (challenge, answers) => ({ spec: 'zkagent/1', tier: 'C', challenge, answers });

// ---------------------------------------------------------------- predicate vocabulary and fixed boundaries ----

test('every fixed boundary is accepted and each off-list neighbour is refused (non-vacuous)', () => {
  assert.deepEqual([...TIER_C_EXPIRY_MONTHS], [3, 6, 12]);
  assert.deepEqual([...TIER_C_DOB_AGES], [15, 16, 18, 21, 60, 65]);
  let accepted = 0;
  for (const months of TIER_C_EXPIRY_MONTHS) {
    assert.equal(validateTierCPredicates([{ name: 'expiry_beyond', months }]).ok, true); accepted += 1;
  }
  for (const age of TIER_C_DOB_AGES) {
    assert.equal(validateTierCPredicates([{ name: 'dob_at_least', age }]).ok, true); accepted += 1;
  }
  assert.equal(accepted, 9);
  let refused = 0;
  for (const months of [0, 1, 2, 4, 5, 7, 9, 11, 13, 24, -3, 3.5, '3', null, NaN]) {
    const r = validateTierCPredicates([{ name: 'expiry_beyond', months }]);
    assert.deepEqual(r.ok, false); assert.ok(['boundary_off_list'].includes(r.reason)); refused += 1;
  }
  for (const age of [0, 14, 17, 19, 20, 22, 59, 61, 64, 66, 100, 18.5, '18', null, NaN]) {
    const r = validateTierCPredicates([{ name: 'dob_at_least', age }]);
    assert.deepEqual(r.ok, false); assert.equal(r.reason, 'boundary_off_list'); refused += 1;
  }
  assert.equal(refused, 30);
});

test('nationality codes are 1 to 3 letters A-Z; an exclusion set holds 1 to 10', () => {
  for (const code of ['D', 'NL', 'NLD', 'usa', ' nld ']) assert.equal(validateTierCPredicates([{ name: 'nationality_is', code }]).ok, true, code);
  for (const code of ['', 'NLDX', 'N1', 'N-D', 'ÄÄ', 7, null, undefined]) {
    const r = validateTierCPredicates([{ name: 'nationality_is', code }]);
    assert.equal(r.ok, false, String(code)); assert.equal(r.reason, 'code_invalid');
  }
  const codes = (n) => Array.from({ length: n }, (_, i) => String.fromCharCode(65 + i, 65 + i, 65 + i));
  assert.equal(validateTierCPredicates([{ name: 'nationality_not_in', codes: codes(TIER_C_MAX_EXCLUSION_SET) }]).ok, true);
  for (const bad of [codes(TIER_C_MAX_EXCLUSION_SET + 1), []]) {
    const r = validateTierCPredicates([{ name: 'nationality_not_in', codes: bad }]);
    assert.equal(r.ok, false); assert.equal(r.reason, 'exclusion_set_size');
  }
  assert.equal(validateTierCPredicates([{ name: 'nationality_not_in', codes: ['CUB', 'C1'] }]).reason, 'code_invalid');
});

test('D118: at most one nationality_is and one nationality_not_in per request, counted by kind', () => {
  const IS = (code) => ({ name: 'nationality_is', code });
  const NOT = (...codes) => ({ name: 'nationality_not_in', codes });
  // two of one kind, however small each set: refused with a fixed reason, never thrown
  for (const bad of [[IS('NLD'), IS('DEU')], [NOT('CUB'), NOT('RUS')], [NOT('CUB'), NAME, NOT('RUS')], [IS('NLD'), NUMBER, IS('USA')]]) {
    const r = validateTierCPredicates(bad);
    assert.equal(r.ok, false, JSON.stringify(bad)); assert.equal(r.reason, 'nationality_asked_more_than_once');
  }
  // one of each kind is allowed, alone and beside the other three kinds (no general cap: 6 predicates here)
  assert.equal(validateTierCPredicates([IS('NLD'), NOT('CUB')]).ok, true);
  assert.equal(validateTierCPredicates([IS('NLD')]).ok, true);
  assert.equal(validateTierCPredicates([NOT('CUB')]).ok, true);
  const all = validateTierCPredicates(ALL_FIVE);
  assert.equal(all.ok, true); assert.equal(all.predicates.length, 6);
  // the other kinds are not capped by D118
  assert.equal(validateTierCPredicates([NAME, NAME, NUMBER, NUMBER, { name: 'expiry_beyond', months: 3 }, { name: 'expiry_beyond', months: 12 }, { name: 'dob_at_least', age: 18 }, { name: 'dob_at_least', age: 21 }]).ok, true);
  // many full-size exclusion sets cannot slip through
  const forty = Array.from({ length: 40 }, () => NOT('AAA', 'BBB', 'CCC', 'DDD', 'EEE', 'FFF', 'GGG', 'HHH', 'III', 'JJJ'));
  assert.equal(validateTierCPredicates(forty).reason, 'nationality_asked_more_than_once');
});

test('D118: issuing refuses two of one nationality kind, loudly, and verify() refuses a signed challenge that carries them', async () => {
  const v = makeVerifier();
  assert.throws(() => issue(v, [{ name: 'nationality_is', code: 'NLD' }, { name: 'nationality_is', code: 'DEU' }]), /nationality_asked_more_than_once/);
  assert.throws(() => issue(v, [{ name: 'nationality_not_in', codes: ['CUB'] }, { name: 'nationality_not_in', codes: ['RUS'] }]), /nationality_asked_more_than_once/);
  // a challenge minted validly, then given a second question and re-signed by the trusted issuer
  const c = issue(v, [{ name: 'nationality_is', code: 'NLD' }]);
  const forged = { ...c, predicates: [...c.predicates, { name: 'nationality_is', code: 'DEU' }] };
  delete forged.sig;
  forged.sig = edSign(null, Buffer.from(canonicalize(forged), 'utf8'), ISSUER.privateKey).toString('base64');
  const out = await v.verify(present(forged, answersFor(forged.predicates)), { now: T0 });
  assert.deepEqual([out.ok, out.allowed], [true, false]);
  assert.equal(out.reason, 'challenge_predicates_invalid');
});

test('codes are normalised to upper case; name and number values are kept as given', () => {
  const r = validateTierCPredicates(ALL_FIVE);
  assert.equal(r.ok, true);
  assert.deepEqual(r.predicates[2], { name: 'nationality_is', code: 'NLD' });
  assert.deepEqual(r.predicates[3], { name: 'nationality_not_in', codes: ['CUB', 'RUS'] });
  assert.deepEqual(r.predicates[4], NAME);
  assert.deepEqual(r.predicates[5], NUMBER);
});

test('name match takes two labelled fields, both non-blank; a single combined string is refused', () => {
  assert.equal(validateTierCPredicates([NAME]).ok, true);
  for (const bad of [
    { name: 'name_match', given_names: 'Testa Mina' },
    { name: 'name_match', surname: 'Examplesson' },
    { name: 'name_match', given_names: '', surname: 'Examplesson' },
    { name: 'name_match', given_names: '  ', surname: 'Examplesson' },
    { name: 'name_match', given_names: '<<', surname: 'Examplesson' },
    { name: 'name_match', given_names: 'Testa Mina', surname: '-' },
    { name: 'name_match', full_name: 'Testa Mina Examplesson' },
    { name: 'name_match', given_names: 'Testa Mina', surname: 'Examplesson', full_name: 'x' },
  ]) {
    assert.equal(validateTierCPredicates([bad]).ok, false, JSON.stringify(Object.keys(bad)));
  }
  for (const bad of [{ name: 'document_number_is', number: '' }, { name: 'document_number_is', number: ' <<< ' }, { name: 'document_number_is', number: 5 }]) {
    assert.equal(validateTierCPredicates([bad]).reason, 'number_invalid');
  }
  assert.equal(validateTierCPredicates([{ name: 'document_number_is' }]).reason, 'predicate_malformed');
});

test('out-of-vocabulary names, extra keys, non-objects and non-lists are refused, never thrown on', () => {
  for (const bad of [
    [{ name: 'dob_exact' }], [{ name: 'full_mrz' }], [{}], [null], [7], ['expiry_beyond'], [[]],
    [{ name: 'expiry_beyond', months: 3, extra: 1 }], [{ name: '__proto__' }], [{ name: 'toString' }],
  ]) assert.equal(validateTierCPredicates(bad).ok, false, JSON.stringify(bad));
  for (const bad of [undefined, null, [], {}, 'x', 1]) assert.equal(validateTierCPredicates(bad).reason, 'predicates_invalid');
});

test('labels carry the name and fixed boundary only, never a name, number or code', () => {
  assert.equal(tierCPredicateLabel({ name: 'expiry_beyond', months: 6 }), 'expiry_beyond:6');
  assert.equal(tierCPredicateLabel({ name: 'dob_at_least', age: 21, result: true }), 'dob_at_least:21');
  assert.equal(tierCPredicateLabel(NAME), 'name_match');
  assert.equal(tierCPredicateLabel(NUMBER), 'document_number_is');
  assert.equal(tierCPredicateLabel(null), 'unknown');
});

// ---------------------------------------------------------------- challenge issuance ----

test('a tier-C challenge is signed, carries predicates and no threshold', () => {
  const v = makeVerifier();
  const c = issue(v, ALL_FIVE);
  assert.equal(c.tier, 'C');
  assert.equal(c.threshold, null);
  assert.deepEqual(c.predicates.map((p) => p.name), ALL_FIVE.map((p) => p.name));
  assert.equal(typeof c.sig, 'string');
  assert.equal(c.key_id, 'iss');
});

test('issuing refuses (loudly, at config time) invalid predicates, a threshold alongside them, or a non-C tier', () => {
  const v = makeVerifier();
  assert.throws(() => issue(v, [{ name: 'dob_at_least', age: 19 }]), /boundary_off_list/);
  assert.throws(() => issue(v, []), /predicates_invalid/);
  assert.throws(() => v.issueChallenge({ tier: 'B', predicates: [NAME], ttlMs: 1000, now: T0 }), /tier 'C' only/);
  assert.throws(() => issueChallenge({
    tier: 'C', predicates: [NAME], threshold: 18, ttlMs: 1000, challengeSecret: SECRET, now: T0, issuer,
  }), /no threshold/);
  // The A/B path is unchanged: a threshold is still required there.
  assert.throws(() => issueChallenge({ tier: 'A', ttlMs: 1000, challengeSecret: SECRET }), /threshold/);
});

// ---------------------------------------------------------------- answers verification ----

test('round trip: well-formed answers verify, and only name, boundary and boolean come back', async () => {
  const v = makeVerifier();
  const c = issue(v, ALL_FIVE);
  const a = answersFor(c.predicates, true); a[2].result = false; // a legitimate "no" among the answers
  const out = await v.verify(present(c, a), { now: T0 });
  assert.equal(out.ok, true); assert.equal(out.allowed, true); assert.equal(out.tier, 'C'); assert.equal(out.reason, 'answers-received');
  assert.deepEqual(out.answers.map((x) => x.result), [true, true, false, true, true, true]);
  assert.deepEqual(out.answers[0], { name: 'expiry_beyond', months: 6, result: true });
  assert.deepEqual(out.answers[1], { name: 'dob_at_least', age: 21, result: true });
  assert.deepEqual(out.answers[4], { name: 'name_match', result: true });
  const dump = JSON.stringify(out);
  for (const secret of ['Testa', 'Examplesson', 'XX0000000', 'NLD', 'CUB', 'RUS']) assert.equal(dump.includes(secret), false, secret);
});

test('answers must match the challenge one for one, in order, with the boundary echoed', async () => {
  const mk = async (mutate) => {
    const v = makeVerifier(); const c = issue(v, ALL_FIVE);
    return v.verify(present(c, mutate(answersFor(c.predicates), c)), { now: T0 });
  };
  const cases = {
    'one answer short': (a) => a.slice(0, -1),
    'one extra answer': (a) => [...a, { name: 'name_match', result: true }],
    'reordered': (a) => [a[1], a[0], ...a.slice(2)],
    'wrong name': (a) => { a[4].name = 'document_number_is'; return a; },
    'boundary not echoed': (a) => { delete a[0].months; return a; },
    'boundary changed': (a) => { a[0].months = 3; return a; },
    'age boundary changed': (a) => { a[1].age = 18; return a; },
    'value smuggled into an answer': (a) => { a[4].value = 'Examplesson'; return a; },
    'boundary on a kind that has none': (a) => { a[2].months = 3; return a; },
    'answers is an object': () => ({ 0: { name: 'x' } }),
  };
  for (const [label, mutate] of Object.entries(cases)) {
    const out = await mk(mutate);
    assert.equal(out.ok, true, label); assert.equal(out.allowed, false, label);
    assert.ok(['answers_do_not_match_request', 'presentation_malformed'].includes(out.reason), `${label}: ${out.reason}`);
  }
  const notBool = await mk((a) => { a[0].result = 'true'; return a; });
  assert.deepEqual([notBool.ok, notBool.allowed, notBool.reason], [true, false, 'answer_not_boolean']);
  const nul = await mk((a) => { a[3].result = null; return a; });
  assert.equal(nul.reason, 'answer_not_boolean');
});

test('the challenge is checked first: forged, tampered predicates, unsigned, expired and replayed are all refused', async () => {
  const v = makeVerifier();
  const c = issue(v, [NAME, { name: 'expiry_beyond', months: 3 }]);
  const a = answersFor(c.predicates);

  // predicates edited after signing -> signature breaks
  const tampered = { ...c, predicates: [NAME] };
  assert.equal((await v.verify(present(tampered, [a[0]]), { now: T0 })).reason, 'signature_invalid');

  // unsigned tier-C challenge
  const unsigned = issue(v, [NAME], { issuer: null });
  assert.equal((await v.verify(present(unsigned, answersFor(unsigned.predicates)), { now: T0 })).reason, 'signature_required_at_tier_c');

  // a forged nonce
  assert.equal((await v.verify(present({ ...c, nonce: 'A'.repeat(75) }, a), { now: T0 })).reason, 'nonce_forged');

  // expired
  const expired = await v.verify(present(c, a), { now: T0 + 61_000 });
  assert.deepEqual([expired.ok, expired.allowed, expired.reason], [true, false, 'challenge_expired']);

  // replay: the first presentation is spent, whatever its outcome
  const first = await v.verify(present(c, a), { now: T0 });
  assert.equal(first.allowed, true);
  const again = await v.verify(present(c, a), { now: T0 });
  assert.deepEqual([again.ok, again.allowed, again.reason], [true, false, 'nonce_replayed']);

  // an unknown signer
  const other = generateKeyPairSync('ed25519');
  const foreign = v.issueChallenge({ tier: 'C', predicates: [NAME], ttlMs: 60_000, now: T0, issuer: { privateKey: other.privateKey, key_id: 'iss' } });
  assert.equal((await v.verify(present(foreign, answersFor(foreign.predicates)), { now: T0 })).reason, 'signature_invalid');
});

test('a verifier whose max tier is below C refuses tier-C answers; a tier-A/B challenge cannot carry them', async () => {
  const lowV = makeVerifier({ tiers: { max: 'B' } });
  const c = issue(lowV, [NAME]);
  const out = await lowV.verify(present(c, answersFor(c.predicates)), { now: T0 });
  assert.deepEqual([out.ok, out.allowed, out.reason], [true, false, 'tier_exceeds_max']);

  const v = makeVerifier();
  const bChallenge = v.issueChallenge({ tier: 'B', ttlMs: 60_000, now: T0, issuer });
  const out2 = await v.verify(present(bChallenge, []), { now: T0 });
  assert.deepEqual([out2.ok, out2.allowed, out2.reason], [true, false, 'tier_below_requested']);

  // a tier-C challenge with NO predicates (the pre-item-14 shape) cannot be answered
  const legacy = v.issueChallenge({ tier: 'C', threshold: 18, ttlMs: 60_000, now: T0, issuer });
  const out3 = await v.verify(present(legacy, []), { now: T0 });
  assert.deepEqual([out3.ok, out3.allowed, out3.reason], [true, false, 'challenge_predicates_invalid']);
});

test('required evidence at tier C cannot be satisfied by answers: could-not-check, never a no', async () => {
  const stub = {
    binds: { nonce: true, claim: true, scope: true }, linkability: 'none', tierCeiling: 'C', verify: () => ({ ok: true, valid: true }),
  };
  const v = makeVerifier({ evidence: { plugs: { 'stub/1': stub }, require: { C: ['stub/1'] } } });
  const c = issue(v, [NAME]);
  const out = await v.verify(present(c, answersFor(c.predicates)), { now: T0 });
  assert.deepEqual([out.ok, out.allowed, out.reason], [false, null, 'tier_c_evidence_unsupported']);
});

test('extra top-level keys on an answers presentation are refused (nothing rides along)', async () => {
  const v = makeVerifier();
  const c = issue(v, [NAME]);
  const out = await v.verify({ ...present(c, answersFor(c.predicates)), zktag: 'abc', claim: { over_threshold: true, threshold: 18 } }, { now: T0 });
  assert.deepEqual([out.ok, out.allowed, out.reason], [true, false, 'presentation_malformed']);
});

test('never throws, and the ok/allowed pairing holds, whatever garbage arrives', async () => {
  const v = makeVerifier();
  const c = issue(v, [NAME]);
  const garbage = [
    present(null, []), present(c, null), present({}, []), present(c, [null]), present(c, [[]]),
    present(c, [{ get name() { throw new Error('boom'); } }]),
    { spec: 'zkagent/1', tier: 'C', answers: [] }, { spec: 'zkagent/1', tier: 'C', answers: 'x', challenge: c },
    { spec: 'zkagent/1', tier: 'Z', answers: [] },
  ];
  for (const g of garbage) {
    const out = await v.verify(g, { now: T0 });
    if (out.ok === false) assert.equal(out.allowed, null); else assert.equal(typeof out.allowed, 'boolean');
    assert.equal(out.ok === false && out.allowed === false, false);
  }
  assert.equal((await v.verify(present(c, answersFor(c.predicates)), { now: NaN })).allowed, null);
});

test('checkTierCAnswers on its own: sanitised output and fixed reasons', () => {
  const ok = checkTierCAnswers([{ name: 'expiry_beyond', months: 12 }, NUMBER], [{ name: 'expiry_beyond', months: 12, result: false }, { name: 'document_number_is', result: true }]);
  assert.deepEqual(ok, { ok: true, answers: [{ name: 'expiry_beyond', months: 12, result: false }, { name: 'document_number_is', result: true }] });
  assert.equal(checkTierCAnswers([{ name: 'dob_at_least', age: 19 }], []).reason, 'challenge_predicates_invalid');
  assert.equal(checkTierCAnswers(undefined, undefined).ok, false);
});

// ---------------------------------------------------------------- the generic refusal (D110) ----

test('access_denied verifies as a distinct, explicit refusal: a real no, never a pass, never ok:false', () => {
  const r = verifyTierCRefusal({ error: 'access_denied', state: 'abc' });
  assert.deepEqual(r, { ok: true, allowed: false, reason: 'refused_by_holder', tier: 'C', refused: true });
  assert.notEqual(r.allowed, true);
  // a reason string cannot change the verdict: the app never sends one, and a site never reads one
  assert.deepEqual(verifyTierCRefusal({ error: 'access_denied', error_description: 'holder_setting_off' }), r);
  assert.deepEqual(verifyTierCRefusal({ error: 'access_denied', error_description: 'age_lock' }), r);
});

test('any other or missing error is could-not-check, never a refusal and never a pass', () => {
  for (const bad of [
    { error: 'server_error' }, { error: 'invalid_request' }, { error: 'ACCESS_DENIED' }, { error: '' }, { error: 7 },
    {}, null, undefined, 'access_denied', [], { state: 'abc' },
  ]) {
    const r = verifyTierCRefusal(bad);
    assert.equal(r.ok, false, JSON.stringify(bad)); assert.equal(r.allowed, null); assert.equal(r.refused, undefined);
  }
  assert.equal(verifyTierCRefusal({ get error() { throw new Error('boom'); } }).ok, false);
});
