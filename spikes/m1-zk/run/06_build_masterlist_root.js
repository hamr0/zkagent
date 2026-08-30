// Builds a PackagedCertificatesFile from either the FULL extracted BSI
// masterlist (588 certs) or a 2-cert stand-in (just the NL+US CSCA actually
// verified against our real DSCs), and computes the zkPassport certificate
// registry Merkle root via calculatePackagedCertificatesRoot. Reports timing.
const fs = require("fs")
const path = require("path")
const { calculatePackagedCertificatesRoot } = require("@zkpassport/utils")
const { convertDerToPackagedCertificateV1, derToPem } = require("./05_build_packaged_cert")

async function main() {
  const mode = process.argv[2] // "full" or "pair"
  const certsDir = "/home/hamr/PycharmProjects/zkagent/spikes/m1-zk/out/masterlist-certs"
  let derFiles
  if (mode === "full") {
    derFiles = fs
      .readdirSync(certsDir)
      .filter((f) => f.startsWith("csca_") && f.endsWith(".der"))
      .sort()
      .map((f) => path.join(certsDir, f))
  } else {
    derFiles = [path.join(certsDir, "csca_0368.der"), path.join(certsDir, "csca_0500.der")]
  }

  const packaged = []
  const errors = []
  const t0 = Date.now()
  for (const f of derFiles) {
    const der = fs.readFileSync(f)
    try {
      const pc = await convertDerToPackagedCertificateV1(der)
      packaged.push(pc)
    } catch (e) {
      errors.push({ file: path.basename(f), error: e.message })
    }
  }
  const t1 = Date.now()

  const packagedCertsFile = {
    version: 1,
    timestamp: Math.floor(Date.now() / 1000),
    certificates: packaged,
    masterlists: [],
    revocations: [],
  }

  let root = null
  let rootError = null
  const t2 = Date.now()
  try {
    root = await calculatePackagedCertificatesRoot(packagedCertsFile)
  } catch (e) {
    rootError = e.message
  }
  const t3 = Date.now()

  console.log(
    JSON.stringify(
      {
        mode,
        candidate_der_files: derFiles.length,
        packaged_ok: packaged.length,
        packaged_errors_count: errors.length,
        packaged_errors_sample: errors.slice(0, 5),
        convert_ms: t1 - t0,
        root_compute_ms: t3 - t2,
        root,
        root_error: rootError,
      },
      null,
      2,
    ),
  )
  if (root) {
    fs.writeFileSync(
      path.join(certsDir, `packaged-certs-${mode}.json`),
      JSON.stringify(packagedCertsFile, null, 2),
    )
    fs.writeFileSync(path.join(certsDir, `root-${mode}.txt`), root)
  }
}

main().catch((e) => {
  console.error(JSON.stringify({ ok: false, error: e.message, stack: e.stack }))
  process.exit(1)
})
