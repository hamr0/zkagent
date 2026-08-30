import test from 'node:test';
import assert from 'node:assert/strict';
import { canonicalize, sha256 } from '../../src/canonical.js';

test('key order independence: differently-ordered keys canonicalize identically', () => {
  const a = { b: 2, a: 1, c: { y: 2, x: 1 } };
  const b = { a: 1, c: { x: 1, y: 2 }, b: 2 };
  assert.equal(canonicalize(a), canonicalize(b));
});

test('sha256(canonicalize(...)) is deterministic under key reordering', () => {
  const a = { threshold: 18, tier: 'A', verbs: ['age'] };
  const b = { verbs: ['age'], tier: 'A', threshold: 18 };
  assert.deepEqual(sha256(a), sha256(b));
});

test('arrays keep their given order (order is significant, unlike object keys)', () => {
  assert.notEqual(canonicalize([1, 2, 3]), canonicalize([3, 2, 1]));
  assert.equal(canonicalize([1, 2, 3]), '[1,2,3]');
});

test('nesting is canonicalized recursively, not just at the top level', () => {
  const nested = { z: { d: 1, c: { g: 1, a: 2 } }, a: 1 };
  assert.equal(canonicalize(nested), '{"a":1,"z":{"c":{"a":2,"g":1},"d":1}}');
});

test('rejects floats -- numbers must be integers or strings', () => {
  assert.throws(() => canonicalize({ x: 1.5 }), TypeError);
  assert.throws(() => canonicalize(0.1), TypeError);
});

test('rejects non-finite numbers', () => {
  assert.throws(() => canonicalize({ x: NaN }), TypeError);
  assert.throws(() => canonicalize({ x: Infinity }), TypeError);
  assert.throws(() => canonicalize({ x: -Infinity }), TypeError);
});

test('accepts integers and strings, including negative and zero', () => {
  assert.doesNotThrow(() => canonicalize({ a: 0, b: -5, c: '1.5' }));
  assert.equal(canonicalize({ a: 0, b: -5, c: '1.5' }), '{"a":0,"b":-5,"c":"1.5"}');
});

test('rejects unsupported value types rather than dropping or coercing them', () => {
  assert.throws(() => canonicalize(undefined), TypeError);
  assert.throws(() => canonicalize(() => {}), TypeError);
  assert.throws(() => canonicalize({ f: () => {} }), TypeError);
  assert.throws(() => canonicalize(Symbol('x')), TypeError);
});

test('booleans and null round-trip as JSON literals', () => {
  assert.equal(canonicalize({ a: true, b: false, c: null }), '{"a":true,"b":false,"c":null}');
});

test('sha256 propagates canonicalize\'s rejection of a float rather than hashing something silently coerced', () => {
  assert.throws(() => sha256({ x: 1.1 }), TypeError);
});
