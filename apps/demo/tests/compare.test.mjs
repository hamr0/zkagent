// apps/demo/tests/compare.test.mjs — pure logic, no server/store needed.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { diffPresentations } from '../compare.mjs';

test('identical payloads -> every row same:true', () => {
  const p = {
    spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, evidence: [],
  };
  const rows = diffPresentations(p, structuredClone(p));
  assert.ok(rows.length > 0);
  for (const r of rows) assert.equal(r.same, true, `${r.field} expected same`);
});

test('nonce+signature differ only -> exactly those two rows same:false', () => {
  const a = {
    spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, challenge: { nonce: 'n1' }, evidence: [], sig: 's1',
  };
  const b = {
    spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, challenge: { nonce: 'n2' }, evidence: [], sig: 's2',
  };
  const rows = diffPresentations(a, b);
  const differing = rows.filter((r) => !r.same).map((r) => r.field).sort();
  assert.deepEqual(differing, ['challenge.nonce', 'sig']);
});

test('a field outside nonce/signature differs -> it is flagged too', () => {
  const a = {
    spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 },
  };
  const b = {
    spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 21 },
  };
  const rows = diffPresentations(a, b);
  const differing = rows.filter((r) => !r.same).map((r) => r.field);
  assert.deepEqual(differing, ['claim.threshold']);
});

test('a field present on only one side gets its own row, flagged as differing', () => {
  const a = { spec: 'zkagent/1' };
  const b = { spec: 'zkagent/1', tier: 'A' };
  const rows = diffPresentations(a, b);
  const tierRow = rows.find((r) => r.field === 'tier');
  assert.ok(tierRow);
  assert.equal(tierRow.same, false);
  assert.equal(tierRow.a, undefined);
  assert.equal(tierRow.b, 'A');
});

test('row order is stable (sorted by field name), independent of input key order', () => {
  const a = { b: 1, a: 2 };
  const b = { a: 2, b: 1 };
  const rows = diffPresentations(a, b);
  assert.deepEqual(rows.map((r) => r.field), ['a', 'b']);
});
