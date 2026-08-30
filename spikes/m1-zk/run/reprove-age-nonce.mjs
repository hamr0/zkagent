// Re-proves the compare/age stage for each real document under a FRESH
// chiproof challenge nonce carried in `service_subscope`, so the chiproof
// zk-passport tests can bind nonce -> proof against real artefacts.
//
// Reads only:  out/<doc>/age2/control/Prover.toml, out/<doc>/age2/bb/vk,
//              vendor/zkpassport-circuits/target/compare_age.json
// Writes only: out/<doc>/age-nonce-<short>/{proof,public_inputs,challenge.json}
//              out/age-nonce-index.json   (holds the runtime-generated test secret)
// All of those paths are gitignored. Nothing document-derived is printed.
// Throwaway spike script: no tests, minimal error handling.
//
// Usage (from spikes/m1-zk):  node run/reprove-age-nonce.mjs [nl us]
//   env NARGO / BB override the toolchain paths.
import { randomBytes, createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, unlinkSync, rmSync, readdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { issueChallenge } from '../../../packages/chiproof/src/index.js';

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const PKG = join(ROOT, 'vendor/zkpassport-circuits/src/noir/bin/compare/age/standard');
const TARGET = join(ROOT, 'vendor/zkpassport-circuits/target');
const NARGO = process.env.NARGO ?? join(process.env.HOME, 'opt/noir/bin/nargo');
const BB = process.env.BB ?? join(process.env.HOME, 'opt/bb/bb');
const docs = process.argv.length > 2 ? process.argv.slice(2) : ['nl', 'us'];

// chiproof's registry rule for zk-passport/1: first 31 bytes of sha256(utf8 nonce), BE.
const subscopeFromNonce = (n) => '0x' + createHash('sha256').update(n, 'utf8').digest().subarray(0, 31).toString('hex');
const secs = (f) => { const s = process.hrtime.bigint(); f(); return Number(process.hrtime.bigint() - s) / 1e9; };

// Wipe previous runs so the index always points at artefacts that exist.
for (const doc of docs) {
  for (const d of readdirSync(join(ROOT, 'out', doc))) {
    if (d.startsWith('age-nonce-')) rmSync(join(ROOT, 'out', doc, d), { recursive: true, force: true });
  }
}

const secret = randomBytes(32);
const now = Date.now();
const index = { challengeSecret: secret.toString('base64'), scopeDomain: 'site-a', now, docs: {} };

for (const doc of docs) {
  const challenge = issueChallenge({ tier: 'A', threshold: 18, ttlMs: 10 * 365 * 24 * 3600 * 1000, challengeSecret: secret, now });
  const short = createHash('sha256').update(challenge.nonce).digest('hex').slice(0, 8);
  const outDir = join(ROOT, 'out', doc, `age-nonce-${short}`);
  mkdirSync(outDir, { recursive: true });

  const toml = readFileSync(join(ROOT, 'out', doc, 'age2/control/Prover.toml'), 'utf8');
  const edited = toml.replace(/^service_subscope = ".*"$/m, `service_subscope = "${subscopeFromNonce(challenge.nonce)}"`);
  if (edited === toml) throw new Error(`${doc}: service_subscope line not found in control Prover.toml`);

  const pname = `Prover-${doc}-nonce-${short}`;
  const wname = `${doc}-nonce-${short}`;
  writeFileSync(join(PKG, `${pname}.toml`), edited);
  let tExec, tProve, tVerify;
  try {
    tExec = secs(() => execFileSync(NARGO, ['execute', '-p', pname, wname], { cwd: PKG, stdio: 'pipe' }));
    tProve = secs(() => execFileSync(BB, ['prove', '-k', join(ROOT, 'out', doc, 'age2/bb/vk'), '-b', join(TARGET, 'compare_age.json'), '-w', join(TARGET, `${wname}.gz`), '-o', outDir], { stdio: 'pipe' }));
    tVerify = secs(() => execFileSync(BB, ['verify', '-k', join(ROOT, 'out', doc, 'age2/bb/vk'), '-p', join(outDir, 'proof'), '-i', join(outDir, 'public_inputs')], { stdio: 'pipe' }));
  } finally {
    // The Prover copy and witness are document-derived: never leave them behind.
    try { unlinkSync(join(PKG, `${pname}.toml`)); } catch {}
    try { unlinkSync(join(TARGET, `${wname}.gz`)); } catch {}
  }
  writeFileSync(join(outDir, 'challenge.json'), JSON.stringify({ challenge, now, scopeDomain: 'site-a' }, null, 1));
  index.docs[doc] = `age-nonce-${short}`;
  console.log(JSON.stringify({ doc, dir: `age-nonce-${short}`, nargo_execute_s: +tExec.toFixed(2), bb_prove_s: +tProve.toFixed(2), bb_verify_s: +tVerify.toFixed(3) }));
}
writeFileSync(join(ROOT, 'out/age-nonce-index.json'), JSON.stringify(index, null, 1));
console.log('wrote out/age-nonce-index.json');
