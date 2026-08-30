// Test-only loader for the gitignored spikes/m1-zk artefacts. Reads by path at
// runtime; nothing here (or in any test) embeds proof bytes, hashes or
// public-input values. Returns `null` when anything needed is absent so the
// callers can skip with a message instead of failing.
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

export const BB_PATH = '/home/hamr/opt/bb/bb';
export const OUT = new URL('../../../../spikes/m1-zk/out/', import.meta.url).pathname;

const STAGE_DIR = (doc, ageDir) => ({
  dsc: join(OUT, doc, 'dsc/bb'),
  id_data: join(OUT, doc, 'bb'),
  integrity: join(OUT, doc, 'integrity/bb'),
  age: join(OUT, doc, ageDir),
});
const VK_PATHS = (doc) => ({
  dsc: join(OUT, doc, 'dsc/bb/vk'),
  id_data: join(OUT, doc, 'bb/vk'),
  integrity: join(OUT, doc, 'integrity/bb/vk'),
  age: join(OUT, doc, 'age2/bb/vk'),
});

export function loadArtefacts() {
  const indexPath = join(OUT, 'age-nonce-index.json');
  if (!existsSync(BB_PATH)) return { skip: `bb not found at ${BB_PATH}` };
  if (!existsSync(indexPath)) return { skip: `no re-proved age artefacts (${indexPath}); run the re-prove step from the B3 report` };
  const index = JSON.parse(readFileSync(indexPath, 'utf8'));
  const docs = {};
  for (const [doc, ageDir] of Object.entries(index.docs)) {
    const dirs = STAGE_DIR(doc, ageDir);
    const vks = VK_PATHS(doc);
    const stages = {};
    for (const st of Object.keys(dirs)) {
      const proof = join(dirs[st], 'proof');
      const pi = join(dirs[st], 'public_inputs');
      if (!existsSync(proof) || !existsSync(pi) || !existsSync(vks[st])) return { skip: `missing ${st} artefacts for ${doc}` };
      stages[st] = {
        proof: readFileSync(proof).toString('base64'),
        public_inputs: readFileSync(pi).toString('base64'),
        vk_sha256: createHash('sha256').update(readFileSync(vks[st])).digest('hex'),
      };
    }
    const { challenge, now, scopeDomain } = JSON.parse(readFileSync(join(dirs.age, 'challenge.json'), 'utf8'));
    docs[doc] = { stages, vks, challenge, now, scopeDomain };
  }
  return {
    docs,
    challengeSecret: Buffer.from(index.challengeSecret, 'base64'),
    scopeDomain: index.scopeDomain,
    now: index.now,
    /** Every pinned VK across documents, per stage (dsc/id_data differ NL vs US). */
    allVks: Object.fromEntries(['dsc', 'id_data', 'integrity', 'age'].map((st) => [
      st, [...new Set(Object.values(docs).map((d) => d.vks[st]))],
    ])),
  };
}
