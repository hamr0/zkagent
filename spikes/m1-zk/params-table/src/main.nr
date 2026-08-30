// Generates chiproof's pinned `zk-passport/1` parameter-commitment table:
// Poseidon2([PROOF_TYPE_AGE, 2, min_age, max_age]) exactly as the vendored
// compare_age library computes it, for min_age 0..=100 with max_age = 0 (the
// convention the m1-zk spike's Prover.toml used: `max_age_required = 0`).
// Public constants only -- nothing here derives from a document.
//
// Run:  nargo execute --target-dir ../out/params-table   (from this dir)
// Output goes to packages/chiproof/src/plugs/zk-passport-params.json.
use compare_age::calculate_param_commitment;

fn main() -> pub [Field; 101] {
    let mut out = [0 as Field; 101];
    for i in 0..101 {
        out[i] = calculate_param_commitment(i as u8, 0);
    }
    out
}
