// Builds getIDDataCircuitInputs() from a real DG1+SOD fixture and writes a
// Prover.toml for the matching Noir circuit. Does not print DG1/MRZ contents
// to stdout — only byte-array lengths and non-PII metadata. The Prover.toml
// itself contains the DG1 bytes (this is unavoidable input to the circuit) and
// stays under spikes/m1-zk/out/ or fixtures/real/, both gitignored.
const fs = require("fs")
const path = require("path")
const { Binary, PassportReader, getIDDataCircuitInputs, getTBSMaxLen } = require("@zkpassport/utils")

function toTomlValue(v) {
  if (Array.isArray(v)) {
    return "[" + v.map((x) => (typeof x === "number" ? x : `"${x}"`)).join(", ") + "]"
  }
  if (typeof v === "number") return String(v)
  // strings: field elements / hex come back as "0x..." strings from the library
  return `"${v}"`
}

function writeProverToml(inputs, outPath) {
  const lines = []
  for (const [k, v] of Object.entries(inputs)) {
    lines.push(`${k} = ${toTomlValue(v)}`)
  }
  fs.writeFileSync(outPath, lines.join("\n") + "\n")
}

async function main() {
  const docId = process.argv[2]
  if (!docId) {
    console.error("usage: node 02_build_inputs.js <fixture-id-prefix> <out-dir>")
    process.exit(1)
  }
  const outDir = process.argv[3]
  const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
  const dg1Bytes = fs.readFileSync(path.join(FIXTURES, `${docId}.dg1`))
  const sodBytes = fs.readFileSync(path.join(FIXTURES, `${docId}.sod`))

  const dg1 = Binary.from(dg1Bytes)
  const sod = Binary.from(sodBytes)
  const reader = new PassportReader()
  reader.loadPassport(dg1, sod)
  const vm = reader.getPassportViewModel()

  const saltIn = 1n
  const saltOut = 2n
  const inputs = await getIDDataCircuitInputs(vm, saltIn, saltOut)
  if (!inputs) {
    console.error(JSON.stringify({ ok: false, error: "getIDDataCircuitInputs returned null/undefined" }))
    process.exit(1)
  }

  fs.mkdirSync(outDir, { recursive: true })
  const proverPath = path.join(outDir, "Prover.toml")
  writeProverToml(inputs, proverPath)

  const report = {
    doc_id: docId,
    tbs_max_len: getTBSMaxLen(vm),
    tbs_certificate_length: inputs.tbs_certificate.length,
    dg1_length: inputs.dg1.length,
    signed_attributes_length: inputs.signed_attributes.length,
    e_content_length: inputs.e_content.length,
    exponent: inputs.exponent,
    pss_salt_len: inputs.pss_salt_len,
    prover_toml_written_to: proverPath,
    prover_toml_byte_size: fs.statSync(proverPath).size,
  }
  console.log(JSON.stringify(report, null, 2))
}

main().catch((e) => {
  console.error(JSON.stringify({ ok: false, error: e.message }))
  process.exit(1)
})
