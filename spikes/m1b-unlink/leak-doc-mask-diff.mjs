// Step 4: does the proof BYTES themselves (excluding vk_sha256 and all
// public_inputs) carry a document-constant fingerprint visible to a passive
// observer with no public inputs at all? Build a per-document constant mask
// (positions identical across all of that doc's own m1b presentations),
// then compare the NL mask's constant VALUES against the US mask's constant
// VALUES at positions masked in both. No document-derived byte values are
// printed, only counts/lengths/offsets.
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const STAGES = ['dsc', 'id_data'];

function loadPresentation(doc, siteRun) {
  return JSON.parse(readFileSync(join(M1ZK, 'out', doc, 'm1b', siteRun, 'presentation.json'), 'utf8'));
}
function stagesOf(p) { return p.evidence[0].data.stages; }

const RUNS = {
  nl: ['site-a-r1', 'site-a-r2', 'site-b-r1'],
  us: ['site-a-r1', 'site-a-r2', 'site-b-r1'],
};

function computeMask(buffers) {
  const lens = new Set(buffers.map((b) => b.length));
  if (lens.size !== 1) return { mismatchedLengths: [...lens] };
  const len = buffers[0].length;
  const mask = new Uint8Array(len);
  for (let i = 0; i < len; i += 1) {
    const v = buffers[0][i];
    let same = true;
    for (let k = 1; k < buffers.length; k += 1) { if (buffers[k][i] !== v) { same = false; break; } }
    mask[i] = same ? 1 : 0;
  }
  return { mask, sample: buffers[0], len };
}
function maskSize(mask) { let n = 0; for (const b of mask) n += b; return n; }

function main() {
  const report = { proof_length_equal_nl_vs_us: {}, per_doc_mask: {}, cross_doc_constant_diff: {} };
  const docBufs = {};
  for (const doc of ['nl', 'us']) {
    docBufs[doc] = {};
    for (const st of STAGES) {
      const presentations = RUNS[doc].map((sr) => loadPresentation(doc, sr));
      const buffers = presentations.map((p) => Buffer.from(stagesOf(p)[st].proof, 'base64'));
      docBufs[doc][st] = buffers;
    }
  }
  for (const st of STAGES) {
    const nlLen = docBufs.nl[st][0].length;
    const usLen = docBufs.us[st][0].length;
    report.proof_length_equal_nl_vs_us[st] = { nl_len: nlLen, us_len: usLen, equal: nlLen === usLen };

    const nlMaskR = computeMask(docBufs.nl[st]);
    const usMaskR = computeMask(docBufs.us[st]);
    report.per_doc_mask[st] = {
      nl: nlMaskR.mismatchedLengths ? { error: 'length mismatch', lens: nlMaskR.mismatchedLengths } : { total: nlMaskR.len, masked: maskSize(nlMaskR.mask) },
      us: usMaskR.mismatchedLengths ? { error: 'length mismatch', lens: usMaskR.mismatchedLengths } : { total: usMaskR.len, masked: maskSize(usMaskR.mask) },
    };

    if (!nlMaskR.mismatchedLengths && !usMaskR.mismatchedLengths && nlLen === usLen) {
      // positions constant in BOTH nl's own corpus and us's own corpus
      let bothMasked = 0;
      let disagree = 0;
      const len = nlLen;
      for (let i = 0; i < len; i += 1) {
        if (nlMaskR.mask[i] && usMaskR.mask[i]) {
          bothMasked += 1;
          if (nlMaskR.sample[i] !== usMaskR.sample[i]) disagree += 1;
        }
      }
      report.cross_doc_constant_diff[st] = { positions_masked_in_both_docs: bothMasked, positions_where_values_disagree: disagree };
    } else {
      report.cross_doc_constant_diff[st] = { note: 'cannot compare directly, length mismatch' };
    }
  }
  console.log(JSON.stringify(report, null, 1));
}
main();
