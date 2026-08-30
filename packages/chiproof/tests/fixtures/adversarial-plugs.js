// Test-only adversarial plugs (M1 spec §5 B3). NEVER shipped: they exist to
// attack the evidence slot, and each one is a fixture, not a mock of behaviour.
const BINDS = Object.freeze({ nonce: true, claim: true, scope: true });

/** Always throws from verify() -- must surface as ok:false, never allowed:false. */
export const alwaysThrows = Object.freeze({
  binds: BINDS, linkability: 'none', tierCeiling: 'C',
  verify() { throw new Error('plug exploded'); },
});

/** Rejects (async throw) -- same expectation as alwaysThrows. */
export const alwaysRejects = Object.freeze({
  binds: BINDS, linkability: 'none', tierCeiling: 'C',
  async verify() { throw new Error('plug rejected'); },
});

/** Declares device-class linkability; verifies anything. Refused at tier A. */
export const deviceClass = Object.freeze({
  binds: BINDS, linkability: 'device', tierCeiling: 'C',
  verify() { return { ok: true, valid: true, reason: 'device_ok' }; },
});

/** Cannot bind the nonce -- must be refused at registration, never reach verify. */
export const cannotBindNonce = Object.freeze({
  binds: Object.freeze({ nonce: false, claim: true, scope: true }), linkability: 'none', tierCeiling: 'C',
  verify() { return { ok: true, valid: true, reason: 'should_never_run' }; },
});

/** Unlinkable, always valid, optionally dated -- a stand-in for a zk-style plug. */
export function alwaysValid({ expiresAt } = {}) {
  return Object.freeze({
    binds: BINDS, linkability: 'none', tierCeiling: 'C',
    verify() { return expiresAt === undefined ? { ok: true, valid: true, reason: 'fixture_ok' } : { ok: true, valid: true, reason: 'fixture_ok', expiresAt }; },
  });
}

/** Reports it could not check -- must be ok:false through the slot. */
export const cannotCheckPlug = Object.freeze({
  binds: BINDS, linkability: 'none', tierCeiling: 'C',
  verify() { return { ok: false, valid: null, reason: 'fixture_backend_down' }; },
});

/** Returns garbage instead of a result object. */
export const returnsGarbage = Object.freeze({
  binds: BINDS, linkability: 'none', tierCeiling: 'C',
  verify() { return 'yes'; },
});

/** Ceiling B: presenting tier C with this evidence must be refused. */
export const ceilingB = Object.freeze({
  binds: BINDS, linkability: 'none', tierCeiling: 'B',
  verify() { return { ok: true, valid: true, reason: 'fixture_ok' }; },
});
