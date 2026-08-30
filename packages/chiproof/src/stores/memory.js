// SPDX-License-Identifier: Apache-2.0
/**
 * A non-durable, single-process NonceStore. FOR TESTS ONLY. Ported from 8een's
 * `InMemoryNonceStore` (https://github.com/hamr0/8een `src/challenge.js:207-254`,
 * Apache-2.0), renamed to this package's store interface.
 *
 * It satisfies `NonceStore { setIfAbsent(key, ttlMs): Promise<boolean> }` with a
 * plain Map, so `spendNonce` works out of the box in one process. It does NOT
 * survive a restart and — the reason it must never back a real deployment — it is
 * NOT shared across processes: behind two replicas, a replay routed to the
 * replica that did not see the first use is ACCEPTED. `createVerifier` refuses to
 * boot with this store outside `NODE_ENV==='test'` unless `allowInMemoryStore:true`
 * is passed explicitly (src/index.js) — the same "you must ask for this out loud"
 * guard 8een enforces at its own boundary.
 */
export class InMemoryNonceStore {
  /** @type {Map<string, number>} key -> expiry (ms since epoch) */
  #seen = new Map();

  constructor({ quiet = false } = {}) {
    if (!quiet) {
      // eslint-disable-next-line no-console
      console.warn(
        '[chiproof] InMemoryNonceStore is for tests only: it is not shared across '
        + 'processes and does not survive a restart, so it does NOT stop replays '
        + 'behind multiple replicas. Use a real atomic store (e.g. Redis SET NX PX) '
        + 'in any real deployment.',
      );
    }
  }

  /**
   * @param {string} key
   * @param {number} ttlMs
   * @returns {Promise<boolean>} true if newly recorded (first use), false if replay
   */
  async setIfAbsent(key, ttlMs) {
    const now = Date.now();
    const existing = this.#seen.get(key);
    // `>= now` (inclusive): a key is still "spent" AT its expiry instant, so a
    // replay arriving in the exact expiry millisecond -- where the caller hands
    // us ttlMs 0 -- is still refused rather than slipping through on `>`.
    if (existing !== undefined && existing >= now) return false;
    this.#seen.set(key, now + Math.max(0, Number(ttlMs) || 0));
    // Opportunistic sweep so the map cannot grow without bound under load.
    if (this.#seen.size > 1024) {
      for (const [k, exp] of this.#seen) if (exp < now) this.#seen.delete(k);
    }
    return true;
  }
}
