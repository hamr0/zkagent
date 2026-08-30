// Builds getDSCCircuitInputs() (the "DSC signed by CSCA" claim) using the
// REAL BSI-derived masterlist (588 certs, extracted from spikes/m0's bundled
// asset) as the certificate registry, and writes a Prover.toml for the
// matching sig-check/dsc circuit variant.
const fs = require("fs")
const path = require("path")
const { Binary, PassportReader, getDSCCircuitInputs, getTBSMaxLen } = require("@zkpassport/utils")
const { writeProverToml } = require("./toml-writer")

async function main() {
  const docId = process.argv[2]
  const outDir = process.argv[3]
  const packagedCertsPath =
    process.argv[4] || "/home/hamr/PycharmProjects/zkagent/spikes/m1-zk/out/masterlist-certs/packaged-certs-full.json"

  const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
  const dg1Bytes = fs.readFileSync(path.join(FIXTURES, `${docId}.dg1`))
  const sodBytes = fs.readFileSync(path.join(FIXTURES, `${docId}.sod`))
  const dg1 = Binary.from(dg1Bytes)
  const sod = Binary.from(sodBytes)
  const reader = new PassportReader()
  reader.loadPassport(dg1, sod)
  const vm = reader.getPassportViewModel()

  const packagedCertsFile = JSON.parse(fs.readFileSync(packagedCertsPath, "utf8"))

  const t0 = Date.now()
  const inputs = await getDSCCircuitInputs(vm, 1n, packagedCertsFile)
  const t1 = Date.now()
  if (!inputs) {
    console.error(JSON.stringify({ ok: false, error: "getDSCCircuitInputs returned null/undefined (CSCA not found in registry?)" }))
    process.exit(1)
  }

  fs.mkdirSync(outDir, { recursive: true })
  const proverPath = path.join(outDir, "Prover.toml")
  writeProverToml(inputs, proverPath)

  console.log(
    JSON.stringify(
      {
        doc_id: docId,
        build_ms: t1 - t0,
        certificate_registry_root: inputs.certificate_registry_root,
        certificate_tree_index: inputs.certificate_tree_index,
        country: inputs.country,
        csc_expiry: inputs.csc_expiry,
        tbs_certificate_len: inputs.tbs_certificate.length,
        csc_pubkey_len: inputs.csc_pubkey.length,
        exponent: inputs.exponent,
        pss_salt_len: inputs.pss_salt_len,
        prover_toml_written_to: proverPath,
      },
      null,
      2,
    ),
  )
}

main().catch((e) => {
  console.error(JSON.stringify({ ok: false, error: e.message }))
  process.exit(1)
})
