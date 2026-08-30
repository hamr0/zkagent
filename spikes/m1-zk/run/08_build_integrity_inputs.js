// Builds getIntegrityCheckCircuitInputs() with salt_in matched to the actual
// id-data circuit's comm_out (salt_out=2n) so the chain genuinely links:
// dsc(comm_out) == id-data(comm_in), id-data(comm_out) == integrity(comm_in).
const fs = require("fs")
const path = require("path")
const { Binary, PassportReader, getIntegrityCheckCircuitInputs } = require("@zkpassport/utils")
const { writeProverToml } = require("./toml-writer")

async function main() {
  const docId = process.argv[2]
  const outDir = process.argv[3]
  const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
  const dg1 = Binary.from(fs.readFileSync(path.join(FIXTURES, `${docId}.dg1`)))
  const sod = Binary.from(fs.readFileSync(path.join(FIXTURES, `${docId}.sod`)))
  const reader = new PassportReader()
  reader.loadPassport(dg1, sod)
  const vm = reader.getPassportViewModel()

  const salts = { dg1Salt: 3n, expiryDateSalt: 3n, dg2HashSalt: 3n, privateNullifierSalt: 3n }
  const inputs = await getIntegrityCheckCircuitInputs(vm, 2n, salts) // saltIn=2n matches id-data's saltOut=2n
  if (!inputs) {
    console.error(JSON.stringify({ ok: false, error: "getIntegrityCheckCircuitInputs returned null" }))
    process.exit(1)
  }
  fs.mkdirSync(outDir, { recursive: true })
  const proverPath = path.join(outDir, "Prover.toml")
  writeProverToml(inputs, proverPath)
  console.log(JSON.stringify({ doc_id: docId, comm_in: inputs.comm_in, prover_toml_written_to: proverPath }, null, 2))
}
main().catch((e) => { console.error(JSON.stringify({ ok: false, error: e.message })); process.exit(1) })
