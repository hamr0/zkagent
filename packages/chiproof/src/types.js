// SPDX-License-Identifier: Apache-2.0
/**
 * Shared JSDoc @typedefs (LIBRARY_CONVENTIONS §2 recipe step 6: cross-file
 * shapes live here rather than as hand-written .d.ts, so the generated
 * types/index.d.ts stays derived from JSDoc alone). Pure type declarations —
 * no runtime code. Referenced from other modules as
 * `import('./types.js').X`.
 */

/**
 * The ok/allowed invariant (M1 spec §3): `ok:false` forces `allowed:null`,
 * never `false`. Built only by `cannotCheck`/`realNo`/`yes` (verdict.js).
 *
 * @typedef {{ok: false, allowed: null, reason: string}
 *   | {ok: true, allowed: false, reason: string}
 *   | ({ok: true, allowed: true} & Record<string, unknown>)} Verdict
 */

/**
 * @typedef {object} Claim
 * @property {boolean} over_threshold
 * @property {number} threshold
 */

/**
 * The signed, self-authenticating challenge (PRD D20; M1 spec §3).
 *
 * @typedef {object} Challenge
 * @property {string} nonce
 * @property {'A'|'B'|'C'} tier
 * @property {string[]} verbs
 * @property {number} threshold
 * @property {number|null} max_scan_age
 * @property {number} issued_at
 * @property {number} expires_at
 * @property {string} [key_id]
 * @property {string} [sig]
 */

/**
 * Options accepted by `issueChallenge` (both the bare `challenge.js` export
 * and the verifier's wrapper in `index.js`, which injects `threshold` and
 * `challengeSecret` from its own config and so omits `challengeSecret` here).
 *
 * @typedef {object} IssueChallengeOptions
 * @property {'A'|'B'|'C'} tier
 * @property {string[]} [verbs]
 * @property {number} threshold
 * @property {number|null} [max_scan_age]
 * @property {number} ttlMs
 * @property {{privateKey: unknown, key_id: string}|null} [issuer]
 * @property {number} [now]
 */

/**
 * A presented evidence envelope (M1 spec §4).
 *
 * @typedef {object} EvidenceItem
 * @property {string} type
 * @property {string|number} version
 * @property {unknown} data
 */

/**
 * The full client presentation (M1 spec §3).
 *
 * @typedef {object} Presentation
 * @property {string} spec
 * @property {'A'|'B'|'C'} tier
 * @property {Claim} claim
 * @property {Challenge} challenge
 * @property {string} [zktag]
 * @property {object} [chip_auth]
 * @property {EvidenceItem[]} [evidence]
 */

/**
 * An evidence plug's answer (M1 spec §4, PRD D24).
 *
 * @typedef {object} PlugResult
 * @property {boolean} ok
 * @property {boolean|null} [valid]
 * @property {string} [reason]
 * @property {number} [expiresAt]
 * @property {string[]} [warnings]
 */

/**
 * The context passed to every evidence plug's `verify(item, ctx)` (M1 spec §4).
 *
 * `zktag` is the presented zktag at tier B/C, and `null` at tier A — tier A
 * carries no zktag by construction (D21), so a plug that declares
 * `binds.zktag === true` can never run there (the slot answers
 * `ok:false, reason:'evidence_zktag_unavailable'` instead of calling it).
 *
 * @typedef {object} PlugCtx
 * @property {string} nonce
 * @property {Claim} claim
 * @property {'A'|'B'|'C'} tier
 * @property {string|null} zktag
 * @property {string} scopeDomain
 * @property {string} [masterlistRoot]
 * @property {{name?: string, package: string, certDigest: string, specVersion?: string}[]} trustedClients
 * @property {number} now
 * @property {number|null} maxScanAge
 */

/**
 * An evidence plug's registration-time declaration (M1 spec §4, PRD D24).
 *
 * `binds.zktag` is optional (default `false`): a plug declaring `true`
 * additionally ties its evidence to the presented zktag (`ctx.zktag`), so a
 * presentation carrying valid evidence bound to one zktag cannot be replayed
 * under another. A zktag-binding plug cannot have `tierCeiling: 'A'`
 * (registration throws) — tier A never has a zktag.
 *
 * @typedef {object} Plug
 * @property {{nonce: true, claim: true, scope: true, zktag?: boolean}} binds
 * @property {'none'|'signer'|'device'} linkability
 * @property {'A'|'B'|'C'} tierCeiling
 * @property {(item: EvidenceItem, ctx: PlugCtx) => PlugResult|Promise<PlugResult>} verify
 */

/**
 * An adopter-supplied, atomic single-use nonce store (M1 spec §5, bucket B1).
 * Workers KV is NOT atomic and must not be used to implement this contract
 * directly — see chiproof.context.md, "NonceStore contract".
 *
 * @typedef {object} NonceStore
 * @property {(key: string, ttlMs: number) => Promise<boolean>} setIfAbsent
 */

/**
 * Per-tier evidence requirements: registry keys that MUST be present and
 * valid at each presented tier. A tier absent from the object requires
 * nothing (bare) at that tier. The plain-array form of `evidence.require`
 * remains the instance-global equivalent (same list at every tier).
 *
 * @typedef {object} RequireByTier
 * @property {string[]} [A]
 * @property {string[]} [B]
 * @property {string[]} [C]
 */

/**
 * `createVerifier(config)`'s config shape (M1 spec §2).
 *
 * @typedef {object} VerifierConfig
 * @property {{nonce: NonceStore}} stores
 * @property {Buffer|Uint8Array|string} challengeSecret
 * @property {number} [threshold]
 * @property {{max: 'A'|'B'|'C'}} [tiers]
 * @property {{pubkey: unknown, key_id: string, maxTier: 'A'|'B'|'C'}[]} [trustedChallengeIssuers]
 * @property {{name?: string, package: string, certDigest: string, specVersion?: string}[]} [trustedClients]
 * @property {{require?: string[]|RequireByTier, accept?: string[], plugs?: Record<string, Plug>, maxItems?: number, maxItemBytes?: number}} [evidence]
 * @property {string} scopeDomain
 * @property {string} [masterlistRoot]
 * @property {boolean} [allowInMemoryStore]
 */

export {};
