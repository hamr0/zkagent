// Step 3: verifier-side inference test. With vk_sha256 stripped, simulates
// what "the plug selects among its pinned vks itself" would have to do:
// trial-verify each pinned vk via `bb verify` directly (bypassing the
// current selectVk, which does not implement trial selection) and record
// which pinned vk succeeds, by label only (never a hash/document value).
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const execFileP = promisify(execFile);
const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const BB_PATH = '/home/hamr/opt/bb/bb';

const PINNED = {
  dsc: [
    { label: 'nl', path: join(M1ZK, 'out', 'nl', 'dsc/bb/vk') },
    { label: 'us', path: join(M1ZK, 'out', 'us', 'dsc/bb/vk') },
  ],
  id_data: [
    { label: 'nl', path: join(M1ZK, 'out', 'nl', 'bb/vk') },
    { label: 'us', path: join(M1ZK, 'out', 'us', 'bb/vk') },
  ],
};

function loadStage(doc, siteRun, st) {
  const p = JSON.parse(readFileSync(join(M1ZK, 'out', doc, 'm1b', siteRun, 'presentation.json'), 'utf8'));
  const s = p.evidence[0].data.stages[st];
  return { proof: Buffer.from(s.proof, 'base64'), pi: Buffer.from(s.public_inputs, 'base64') };
}

async function tryVk(vkPath, proof, pi, dir, tag) {
  const proofPath = join(dir, `${tag}.proof`);
  const piPath = join(dir, `${tag}.pi`);
  writeFileSync(proofPath, proof);
  writeFileSync(piPath, pi);
  const start = process.hrtime.bigint();
  try {
    await execFileP(BB_PATH, ['verify', '-k', vkPath, '-p', proofPath, '-i', piPath], { timeout: 30_000 });
    return { ok: true, ms: Number(process.hrtime.bigint() - start) / 1e6 };
  } catch (e) {
    return { ok: false, ms: Number(process.hrtime.bigint() - start) / 1e6 };
  }
}

async function main() {
  const dir = mkdtempSync(join(tmpdir(), 'chiproof-trial-'));
  const results = [];
  try {
    for (const doc of ['nl', 'us']) {
      for (const st of ['dsc', 'id_data']) {
        const { proof, pi } = loadStage(doc, 'site-a-r1', st);
        const trials = [];
        for (const cand of PINNED[st]) {
          const r = await tryVk(cand.path, proof, pi, dir, `${doc}-${st}-${cand.label}`);
          trials.push({ tried_label: cand.label, succeeded: r.ok, ms: +r.ms.toFixed(1) });
        }
        const winner = trials.find((t) => t.succeeded);
        results.push({
          doc, stage: st, trials, recovered_label: winner ? winner.tried_label : null,
        });
      }
    }
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
  console.log(JSON.stringify(results, null, 1));
  const nlDsc = results.find((r) => r.doc === 'nl' && r.stage === 'dsc').recovered_label;
  const usDsc = results.find((r) => r.doc === 'us' && r.stage === 'dsc').recovered_label;
  const nlId = results.find((r) => r.doc === 'nl' && r.stage === 'id_data').recovered_label;
  const usId = results.find((r) => r.doc === 'us' && r.stage === 'id_data').recovered_label;
  console.error(`SUMMARY: dsc nl->${nlDsc} us->${usDsc} (differ=${nlDsc !== usDsc}); id_data nl->${nlId} us->${usId} (differ=${nlId !== usId})`);
}
main().catch((e) => { console.error(e); process.exit(1); });
