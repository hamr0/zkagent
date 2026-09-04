// apps/demo/store.mjs — M3 persistent store (PRD §6.3 item 3/9, D73-D75).
//
// ONE flat JSON file backs three independent pieces of state the demo's own
// verifier instance needs across restarts:
//   - nonces           chiproof's NonceStore contract (setIfAbsent).
//   - attesterBindings chiproof's AttesterStore contract (get/bind), one
//                       independent namespace PER algorithm (ed25519, p256)
//                       -- mirrors server.mjs's prior two separate
//                       InMemoryAttesterStore instances (see makeVerifier():
//                       "not shared BETWEEN the two algorithms either").
//   - zktagsSeen       the demo's OWN tier-B dedupe/blocklist state (§6.3
//                       item 3/4) -- NOT part of any chiproof contract;
//                       chiproof itself stores nothing (D3, FR3).
//
// Every mutation is serialized in-process (single Node process, DP5) via a
// promise-chain queue, then written to disk as write-temp-file -> fsync ->
// rename (atomic on the same filesystem) -> fsync the containing directory.
// No in-memory fallback exists anywhere in this module: if the file can't
// be read/parsed or the directory can't be written at construction time,
// the constructor throws synchronously -- server.mjs lets that reach
// process top-level and exit non-zero (fail closed, item 9). There is
// deliberately no try/catch anywhere in this file that swallows an I/O or
// parse error into a fallback value.

import {
  readFileSync, writeFileSync, renameSync, openSync, fsyncSync, closeSync, mkdirSync, existsSync,
} from 'node:fs';
import { dirname } from 'node:path';
import { randomBytes } from 'node:crypto';

const CURRENT_VERSION = 1;

function emptyDoc() {
  return {
    version: CURRENT_VERSION, nonces: {}, attesterBindings: {}, zktagsSeen: {},
  };
}

/**
 * Length-prefixed join, same construction chiproof's own InMemoryAttesterStore
 * uses internally (`${s.length}:${s}`) -- so no scope/zktag/algo part can
 * collide across the join (e.g. scope="ab" zktag="c" vs scope="a" zktag="bc").
 */
function joinKey(...parts) {
  return parts.map((p) => { const s = String(p); return `${s.length}:${s}`; }).join(':');
}

export class JsonFileStore {
  #path;

  #doc;

  #queue = Promise.resolve();

  /**
   * Synchronous, fail-fast construction: reads (or creates) the store file
   * immediately, so an unwritable/unreadable store path is a startup-time
   * failure, not a failure on the first real request.
   * @param {string} path
   */
  constructor(path) {
    this.#path = path;
    const dir = dirname(path);
    // Throws on a genuinely unwritable parent (permissions, not a directory, etc).
    mkdirSync(dir, { recursive: true });
    if (existsSync(path)) {
      let raw;
      try {
        raw = readFileSync(path, 'utf8');
      } catch (err) {
        throw new Error(`DEMO_STORE_PATH ${path} exists but could not be read: ${err.message}`);
      }
      let parsed;
      try {
        parsed = JSON.parse(raw);
      } catch (err) {
        throw new Error(
          `DEMO_STORE_PATH ${path} exists but is not valid JSON -- refusing to silently `
          + `overwrite it (fail closed, §6.3 item 9): ${err.message}`,
        );
      }
      if (!parsed || typeof parsed !== 'object' || parsed.version !== CURRENT_VERSION) {
        throw new Error(
          `DEMO_STORE_PATH ${path} has an unrecognised shape (expected {version: `
          + `${CURRENT_VERSION}, ...}) -- refusing to silently overwrite it`,
        );
      }
      this.#doc = {
        version: CURRENT_VERSION,
        nonces: parsed.nonces ?? {},
        attesterBindings: parsed.attesterBindings ?? {},
        zktagsSeen: parsed.zktagsSeen ?? {},
      };
    } else {
      this.#doc = emptyDoc();
      // Proves the path is actually writable right now, not on the first
      // real request -- the whole point of failing closed at startup.
      this.#writeSync();
    }
  }

  /** Atomic write-temp-then-rename, fsyncing both the file and its directory. */
  #writeSync() {
    const dir = dirname(this.#path);
    const tmp = `${this.#path}.tmp-${process.pid}-${randomBytes(6).toString('hex')}`;
    const body = JSON.stringify(this.#doc, null, 2);
    const fd = openSync(tmp, 'w');
    try {
      writeFileSync(fd, body);
      fsyncSync(fd);
    } finally {
      closeSync(fd);
    }
    renameSync(tmp, this.#path);
    // fsync the directory entry too, so the rename itself survives a crash,
    // not just the file's contents.
    const dfd = openSync(dir, 'r');
    try {
      fsyncSync(dfd);
    } finally {
      closeSync(dfd);
    }
  }

  /**
   * Serializes every mutation (read-modify-write + disk write) through one
   * promise chain. A single Node process (DP5) has no OS-level thread race,
   * but two concurrent async handlers can still interleave BETWEEN await
   * points without this -- e.g. two direct_post requests both reading
   * "not yet seen" before either writes "now seen".
   */
  #mutate(fn) {
    const result = this.#queue.then(() => {
      const ret = fn(this.#doc);
      this.#writeSync();
      return ret;
    });
    this.#queue = result.then(() => undefined, () => undefined);
    return result;
  }

  /** Reads are queued behind the same chain, so a read issued right after an unawaited write still observes it, not a stale in-memory snapshot. */
  #read(fn) {
    return this.#queue.then(() => fn(this.#doc));
  }

  // ---------------------------------------------------------- NonceStore ----
  /**
   * chiproof's NonceStore contract: `setIfAbsent(key, ttlMs)`.
   * @returns {Promise<boolean>} true = first use (recorded), false = replay
   */
  async setIfAbsent(key, ttlMs) {
    return this.#mutate((doc) => {
      const now = Date.now();
      // Opportunistic pruning of expired nonces on every write -- keeps the
      // file bounded for a long-running demo without a separate GC pass.
      for (const [k, expiry] of Object.entries(doc.nonces)) {
        if (expiry < now) delete doc.nonces[k];
      }
      const existing = doc.nonces[key];
      if (existing !== undefined && existing >= now) return false;
      doc.nonces[key] = now + ttlMs;
      return true;
    });
  }

  // ------------------------------------------------------- AttesterStore ----
  /**
   * One independent {get,bind} view per algorithm namespace, backed by this
   * same file -- chiproof's AttesterStore contract, `get`/`bind`.
   * @param {string} algoLabel e.g. 'ed25519' | 'p256'
   */
  attesterView(algoLabel) {
    const self = this;
    return {
      async get({ scope, zktag }) {
        return self.#read((doc) => {
          const rec = doc.attesterBindings[joinKey(algoLabel, scope, zktag)];
          if (!rec) return undefined;
          return { key_id: rec.key_id, pubkey: Buffer.from(rec.pubkey_b64, 'base64') };
        });
      },
      async bind({
        scope, zktag, key_id: keyId, pubkey,
      }) {
        return self.#mutate((doc) => {
          doc.attesterBindings[joinKey(algoLabel, scope, zktag)] = {
            key_id: keyId, pubkey_b64: Buffer.from(pubkey).toString('base64'), boundAt: Date.now(),
          };
        });
      },
    };
  }

  // -------------------------------------------------- demo's own dedupe ----
  /** §6.3 item 3/4: has this (scope, zktag) minted an allowed tier-B verdict before? */
  async hasZktagBeenSeen(scope, zktag) {
    return this.#read((doc) => Object.hasOwn(doc.zktagsSeen, joinKey(scope, zktag)));
  }

  /** Idempotent: records first-seen-at only on the first call for this pair. */
  async markZktagSeen(scope, zktag) {
    return this.#mutate((doc) => {
      const key = joinKey(scope, zktag);
      if (!Object.hasOwn(doc.zktagsSeen, key)) {
        doc.zktagsSeen[key] = { firstSeenAt: Date.now() };
      }
    });
  }
}

/** Default path: apps/demo/data/store.json, resolved relative to this file (not cwd). */
export function defaultStorePath() {
  return new URL('./data/store.json', import.meta.url).pathname;
}
