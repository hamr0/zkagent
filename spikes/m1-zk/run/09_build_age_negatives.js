// Builds getAgeCircuitInputs() variants for planted negatives:
//  - "control": min_age=18, today = 2026-08-30 (should PASS)
//  - "over200": min_age=200 (holder cannot satisfy -- should FAIL)
//  - "pastdate": today set far in the past so DOB in DG1 implies under 18 (should FAIL)
const fs = require("fs")
const path = require("path")
const { Binary, PassportReader, getAgeCircuitInputs, getServiceScopeHash } = require("@zkpassport/utils")
const { writeProverToml } = require("./toml-writer")

async function build(vm, minAge, dateIso, outDir) {
  const salts = { dg1Salt: 3n, expiryDateSalt: 3n, dg2HashSalt: 3n, privateNullifierSalt: 3n }
  const query = { age: { gte: minAge } }
  const now = Math.floor(new Date(dateIso).getTime() / 1000)
  const serviceScope = getServiceScopeHash("site-a")
  const inputs = await getAgeCircuitInputs(vm, query, salts, 0n, serviceScope, 0n, now)
  fs.mkdirSync(outDir, { recursive: true })
  const proverPath = path.join(outDir, "Prover.toml")
  writeProverToml(inputs, proverPath)
  return { min_age_required: inputs.min_age_required, current_date: inputs.current_date, comm_in: inputs.comm_in, proverPath }
}

async function main() {
  const docId = process.argv[2]
  const outBase = process.argv[3]
  const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
  const dg1 = Binary.from(fs.readFileSync(path.join(FIXTURES, `${docId}.dg1`)))
  const sod = Binary.from(fs.readFileSync(path.join(FIXTURES, `${docId}.sod`)))
  const reader = new PassportReader()
  reader.loadPassport(dg1, sod)
  const vm = reader.getPassportViewModel()

  const results = {}
  results.control = await build(vm, 18, "2026-08-30T00:00:00Z", path.join(outBase, "control"))
  results.over200 = await build(vm, 200, "2026-08-30T00:00:00Z", path.join(outBase, "over200"))
  results.pastdate = await build(vm, 18, "1990-01-01T00:00:00Z", path.join(outBase, "pastdate"))
  console.log(JSON.stringify({ doc_id: docId, ...results }, null, 2))
}
main().catch((e) => { console.error(JSON.stringify({ ok: false, error: e.message })); process.exit(1) })
