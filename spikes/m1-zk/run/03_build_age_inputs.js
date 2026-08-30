// Builds getAgeCircuitInputs() ("over 18" claim) for a given scope string and
// writes a Prover.toml. Prints only non-PII scalars (age thresholds, dates,
// scope hashes) — never salted_dg1's raw value.
const fs = require("fs")
const path = require("path")
const { Binary, PassportReader, getAgeCircuitInputs, getServiceScopeHash } = require("@zkpassport/utils")
const { writeProverToml } = require("./toml-writer")

async function main() {
  const docId = process.argv[2]
  const scopeStr = process.argv[3]
  const outDir = process.argv[4]
  const nullifierSecret = BigInt(process.argv[5] || "123")

  const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
  const dg1Bytes = fs.readFileSync(path.join(FIXTURES, `${docId}.dg1`))
  const sodBytes = fs.readFileSync(path.join(FIXTURES, `${docId}.sod`))

  const dg1 = Binary.from(dg1Bytes)
  const sod = Binary.from(sodBytes)
  const reader = new PassportReader()
  reader.loadPassport(dg1, sod)
  const vm = reader.getPassportViewModel()

  const salts = { dg1Salt: 3n, expiryDateSalt: 3n, dg2HashSalt: 3n, privateNullifierSalt: 3n }
  const query = { age: { gte: 18 } }
  const now = Math.floor(new Date("2026-08-30T00:00:00Z").getTime() / 1000) // fixed "today" for determinism across runs
  const serviceScope = getServiceScopeHash(scopeStr)
  const serviceSubscope = 0n

  const inputs = await getAgeCircuitInputs(
    vm,
    query,
    salts,
    nullifierSecret,
    serviceScope,
    serviceSubscope,
    now,
  )
  if (!inputs) {
    console.error(JSON.stringify({ ok: false, error: "getAgeCircuitInputs returned null/undefined" }))
    process.exit(1)
  }

  fs.mkdirSync(outDir, { recursive: true })
  const proverPath = path.join(outDir, "Prover.toml")
  writeProverToml(inputs, proverPath)

  console.log(
    JSON.stringify(
      {
        doc_id: docId,
        scope_string: scopeStr,
        service_scope_hash: serviceScope.toString(),
        min_age_required: inputs.min_age_required,
        max_age_required: inputs.max_age_required,
        current_date: inputs.current_date,
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
