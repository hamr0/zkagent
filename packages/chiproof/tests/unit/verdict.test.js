import test from 'node:test';
import assert from 'node:assert/strict';
import { cannotCheck, realNo, yes } from '../../src/verdict.js';

test('cannotCheck: ok:false forces allowed:null, never false', () => {
  const v = cannotCheck('store_unreachable');
  assert.equal(v.ok, false);
  assert.equal(v.allowed, null);
  assert.equal(v.reason, 'store_unreachable');
});

test('realNo: a real "no" -- ok:true, allowed:false', () => {
  const v = realNo('under_threshold');
  assert.equal(v.ok, true);
  assert.equal(v.allowed, false);
  assert.equal(v.reason, 'under_threshold');
});

test('yes: a real "yes" -- ok:true, allowed:true, extra merges in', () => {
  const v = yes({ tier: 'A', zktag: undefined });
  assert.equal(v.ok, true);
  assert.equal(v.allowed, true);
  assert.equal(v.tier, 'A');
});

test('yes: extra cannot override ok/allowed, however it tries', () => {
  const v = yes({ ok: false, allowed: false, allowed_: 'no' });
  assert.equal(v.ok, true, 'ok must stay true');
  assert.equal(v.allowed, true, 'allowed must stay true');
});

test('yes: never throws on a non-object extra', () => {
  for (const bad of [null, undefined, 'x', 42, true]) {
    assert.doesNotThrow(() => yes(bad));
    const v = yes(bad);
    assert.equal(v.ok, true);
    assert.equal(v.allowed, true);
  }
});

test('cannotCheck/realNo: never throw and always produce a string reason, for any input', () => {
  const garbage = [undefined, null, 42, {}, [], () => {}, Symbol('x'), '', '  actual reason  '];
  for (const g of garbage) {
    assert.doesNotThrow(() => cannotCheck(g));
    assert.doesNotThrow(() => realNo(g));
    assert.equal(typeof cannotCheck(g).reason, 'string');
    assert.equal(typeof realNo(g).reason, 'string');
  }
});

// The invariant this whole module exists to hold, fuzzed: whatever inputs the
// three factories are handed, `ok:false` must never coexist with `allowed`
// anything but `null`, and `ok:true` must never coexist with `allowed:null`.
test('INVARIANT (fuzz): ok:false + allowed:false is unreachable through any factory input', () => {
  const reasons = [undefined, null, 42, {}, [], 'a_reason', '', Symbol('x'), () => {}, true, false, NaN];
  for (let i = 0; i < 200; i += 1) {
    const reason = reasons[Math.floor(Math.random() * reasons.length)];
    const extra = Math.random() < 0.5
      ? undefined
      : { ok: Math.random() < 0.5, allowed: Math.random() < 0.5, reason, junk: [1, 2, 3] };

    const cc = cannotCheck(reason);
    assert.equal(cc.ok, false);
    assert.equal(cc.allowed, null, 'cannotCheck must never yield allowed !== null');

    const rn = realNo(reason);
    assert.equal(rn.ok, true);
    assert.equal(rn.allowed, false, 'realNo must never yield allowed !== false');

    const y = yes(extra);
    assert.equal(y.ok, true);
    assert.equal(y.allowed, true, 'yes must never yield allowed !== true');
  }
});
