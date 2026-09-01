// SPDX-License-Identifier: Apache-2.0
/**
 * A non-durable, single-process AttesterStore (D38: per-origin mode-B
 * device-key binding, `src/plugs/attester-sig.js`). FOR TESTS/DEMOS ONLY —
 * follows the exact conventions of `InMemoryNonceStore`
 * (`src/stores/memory.js`), renamed to this store's own contract.
 *
 * It satisfies `AttesterStore { get({scope,zktag}), bind({scope,zktag,key_id,pubkey}) }`
 * with a plain Map, so a single-process demo/spike works out of the box. It
 * does NOT survive a restart and is NOT shared across processes: behind two
 * replicas, a first-sight bind on one replica is invisible to the other, so
 * the SAME device can silently rebind under a different key on whichever
 * replica it happens to hit next — exactly the hijack the real contract
 * exists to prevent. Never back a real deployment with this; use a real
 * shared, atomic store (e.g. Redis/Postgres keyed on scope + zktag).
 */
export class InMemoryAttesterStore {
  /** @type {Map<string, {key_id: string, pubkey: Buffer}>} binding key -> binding */
  #bindings = new Map();

  constructor({ quiet = false } = {}) {
    if (!quiet) {
      // eslint-disable-next-line no-console
      console.warn(
        '[chiproof] InMemoryAttesterStore is for tests/demos only: it is not shared '
        + 'across processes and does not survive a restart, so a device can silently '
        + 'rebind under a different key on a sibling replica. Use a real shared, '
        + 'atomic store in any real deployment.',
      );
    }
  }

  /** Binding key: length-prefixed so no scope/zktag pair can collide across the join. */
  #key(scope, zktag) {
    const s = String(scope);
    return `${s.length}:${s}:${String(zktag)}`;
  }

  /**
   * @param {{scope: string, zktag: string}} key
   * @returns {Promise<{key_id: string, pubkey: Buffer}|undefined>}
   */
  async get({ scope, zktag }) {
    return this.#bindings.get(this.#key(scope, zktag));
  }

  /**
   * @param {{scope: string, zktag: string, key_id: string, pubkey: Buffer}} binding
   * @returns {Promise<void>}
   */
  async bind({
    scope, zktag, key_id: keyId, pubkey,
  }) {
    this.#bindings.set(this.#key(scope, zktag), { key_id: keyId, pubkey });
  }
}
