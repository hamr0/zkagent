// apps/demo/compare.mjs — pure field-by-field diff for two tier-A presentation
// payloads (§6.3 item 5, DP3). No I/O, no DOM: importable unmodified from
// Node tests (`node --test`) AND from the browser page as a native ES module
// (`<script type="module" src="/compare.mjs">`, served by GET /compare.mjs in
// server.mjs) -- one source of truth for the diff logic in both places, per
// the "logic in pure functions" lesson (see AGENT_RULES.md / this task's brief).

/**
 * Flatten a plain object into `{ "a.b.c": value }` pairs. Arrays and
 * primitives are treated as leaves (compared by JSON.stringify equality in
 * diffPresentations below) -- a presentation's only array field is
 * `evidence`, which is either empty (tier A) or a short list of plug items;
 * neither needs per-element rows in the comparison table.
 * @param {unknown} obj
 * @param {string} prefix
 * @param {Record<string, unknown>} out
 */
function flatten(obj, prefix = '', out = {}) {
  if (obj !== null && typeof obj === 'object' && !Array.isArray(obj)) {
    for (const [k, v] of Object.entries(obj)) {
      flatten(v, prefix ? `${prefix}.${k}` : k, out);
    }
  } else {
    out[prefix] = obj;
  }
  return out;
}

/**
 * Compare two presentation payloads field-by-field. Returns one row per
 * field present in EITHER payload (fields only one side has still get a
 * row, with the missing side's value `undefined`), sorted by field name for
 * a stable table order.
 * @param {unknown} a
 * @param {unknown} b
 * @returns {{field: string, a: unknown, b: unknown, same: boolean}[]}
 */
export function diffPresentations(a, b) {
  const flatA = flatten(a ?? {});
  const flatB = flatten(b ?? {});
  const fields = [...new Set([...Object.keys(flatA), ...Object.keys(flatB)])].sort();
  return fields.map((field) => {
    const va = flatA[field];
    const vb = flatB[field];
    return { field, a: va, b: vb, same: JSON.stringify(va) === JSON.stringify(vb) };
  });
}
