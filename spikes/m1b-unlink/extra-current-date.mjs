// Follow-up: builds ONE more full presentation (nl, site-b, run 2) using a
// genuinely later real Date.now() than the original corpus (this script runs
// well after corpus.mjs did), to measure whether age.current_date differs
// from nl site-b run 1 under a real elapsed time gap -- the original corpus
// used one shared Date.now() for all 6 builds, which could not show this.
// Verifies the new presentation with real chiproof, and empirically decodes
// the wire current_date field back to a number to confirm its unit/precision
// against what was fed in (measured, not just read from source).
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildPresentation } from './build.mjs';
import { issueChallenge } from '../../packages/chiproof/src/index.js';
import { zkPassport } from '../../packages/chiproof/src/plugs/zk-passport.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const BB_PATH = '/home/hamr/opt/bb/bb';
const VK_PATHS = (doc) => ({
  dsc: join(M1ZK, 'out', doc, 'dsc/bb/vk'),
  id_data: join(M1ZK, 'out', doc, 'bb/vk'),
  integrity: join(M1ZK, 'out', doc, 'integrity/bb/vk'),
  age: join(M1ZK, 'out', doc, 'age2/bb/vk'),
});

async function main() {
  const now = Date.now(); // genuinely later than the original corpus build
  const nowSeconds = Math.floor(now / 1000);
  const challenge = issueChallenge({
    tier: 'A', threshold: 18, ttlMs: 10 * 365 * 24 * 3600 * 1000, challengeSecret: randomBytes(32), now,
  });

  const { stages, timings } = await buildPresentation({
    doc: 'nl', site: 'site-b', run: 'r2', nonce: challenge.nonce, scopeDomain: 'site-b', nowSeconds,
  });
  const presDir = join(M1ZK, 'out', 'nl', 'm1b', 'site-b-r2');
  mkdirSync(presDir, { recursive: true });
  const presentation = {
    spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, challenge,
    evidence: [{ type: 'zk-passport', version: 1, data: { stages } }],
  };
  writeFileSync(join(presDir, 'presentation.json'), JSON.stringify(presentation));
  writeFileSync(join(presDir, 'challenge.json'), JSON.stringify({ challenge, scopeDomain: 'site-b', now }, null, 1));

  // --- diff current_date and challenge timestamp fields against nl b1 -----
  const b1 = JSON.parse(readFileSync(join(M1ZK, 'out', 'nl', 'm1b', 'site-b-r1', 'presentation.json'), 'utf8'));
  const piNew = Buffer.from(stages.age.public_inputs, 'base64');
  const piB1 = Buffer.from(b1.evidence[0].data.stages.age.public_inputs, 'base64');
  const currentDateNew = piNew.subarray(32, 64);
  const currentDateB1 = piB1.subarray(32, 64);
  const equalCurrentDate = currentDateNew.equals(currentDateB1);

  // Decode the wire field back to a number (u64 BE in the low 8 bytes of the
  // 32-byte slot, per zk-passport.js's own decode) to confirm unit/precision
  // empirically against what was fed to getAgeCircuitInputs.
  const decodedSeconds = Number(currentDateNew.readBigUInt64BE(24));
  const decodeMatchesInput = decodedSeconds === nowSeconds;
  const leadingBytesZero = currentDateNew.subarray(0, 24).equals(Buffer.alloc(24));

  const diff = {
    age_current_date_equal_to_b1: equalCurrentDate,
    challenge_issued_at_equal_to_b1: challenge.issued_at === b1.challenge.issued_at,
    challenge_expires_at_equal_to_b1: challenge.expires_at === b1.challenge.expires_at,
    decoded_current_date_matches_fed_nowSeconds: decodeMatchesInput,
    current_date_leading_24_bytes_zero: leadingBytesZero,
    fed_nowSeconds_had_subsecond_component_truncated: now % 1000 !== 0,
  };

  // --- verify with real chiproof (plug-level, as in verify.mjs) -----------
  const plug = zkPassport({ bbPath: BB_PATH, vks: VK_PATHS('nl'), threshold: 18 });
  const verify1 = await plug.verify(presentation.evidence[0], {
    nonce: challenge.nonce, claim: presentation.claim, tier: 'A', scopeDomain: 'site-b', now: Date.now(),
  });

  // --- does max_scan_age gate on THIS field? empirically confirm ----------
  const tooOld = await plug.verify(presentation.evidence[0], {
    nonce: challenge.nonce, claim: presentation.claim, tier: 'A', scopeDomain: 'site-b', now: Date.now(), maxScanAge: 1,
  });
  const unlimited = await plug.verify(presentation.evidence[0], {
    nonce: challenge.nonce, claim: presentation.claim, tier: 'A', scopeDomain: 'site-b', now: Date.now(), maxScanAge: null,
  });

  console.log(JSON.stringify({
    timings, diff, verify: verify1, max_scan_age_tiny: tooOld, max_scan_age_unlimited: unlimited,
  }, null, 1));
}
main().catch((e) => { console.error(e); process.exit(1); });
