// Loads the real DG1+SOD fixture via @zkpassport/utils' PassportReader and reports
// non-PII metadata: parse success, detected signature/hash algorithms, key size,
// and the Noir circuit variant path it selects. Never logs DG1 contents, MRZ,
// names, numbers, or DOB.
const fs = require("fs")
const path = require("path")
const {
  Binary,
  PassportReader,
  getTBSMaxLen,
  isIDSupported,
  isDSCSupported,
  getDSCSignatureAlgorithmHashAlgorithm,
  getSodSignatureAlgorithmHashAlgorithm,
  getDSCCountry,
} = require("@zkpassport/utils")

const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
const dg1Path = path.join(FIXTURES, "id-20260830031213.dg1")
const sodPath = path.join(FIXTURES, "id-20260830031213.sod")

function main() {
  const dg1Bytes = fs.readFileSync(dg1Path)
  const sodBytes = fs.readFileSync(sodPath)

  const dg1 = Binary.from(dg1Bytes)
  const sod = Binary.from(sodBytes)

  const reader = new PassportReader()
  const report = { dg1_byte_length: dg1Bytes.length, sod_byte_length: sodBytes.length }
  try {
    reader.loadPassport(dg1, sod)
    report.sod_parsed = true
  } catch (e) {
    report.sod_parsed = false
    report.parse_error = String(e && e.message ? e.message : e)
    console.log(JSON.stringify(report, null, 2))
    process.exit(1)
  }

  const vm = reader.getPassportViewModel()
  const cert = vm.sod.certificate // the DSC (Document Signer Certificate) parsed from the SOD

  report.dsc_signature_algorithm_hash = getDSCSignatureAlgorithmHashAlgorithm(vm)
  report.sod_signature_algorithm_hash = getSodSignatureAlgorithmHashAlgorithm(vm)
  report.tbs_certificate_signature_algorithm_name = cert.tbs.signatureAlgorithm.name
  report.tbs_max_len = getTBSMaxLen(vm)
  report.tbs_actual_byte_length = cert.tbs.bytes ? cert.tbs.bytes.length : undefined
  report.is_id_supported = isIDSupported(vm)
  report.dsc_country_alpha3 = getDSCCountry(cert) // country code, not PII
  report.is_dsc_supported = isDSCSupported(cert)

  const spki = cert.tbs.subjectPublicKeyInfo
  report.dsc_public_key_algorithm_name = spki.signatureAlgorithm.name
  const derPubkeyLen = spki.subjectPublicKey && spki.subjectPublicKey.bytes ? spki.subjectPublicKey.bytes.length : undefined
  report.dsc_public_key_der_byte_length = derPubkeyLen // 270 bytes DER matches an RSA-2048 SubjectPublicKeyInfo

  // Which pre-generated Noir circuit variant this selects (per Nargo.toml layout
  // observed in vendor/zkpassport-circuits: sig-check/id-data/tbs_<len>/<alg>/.../<hash>)
  const isRSA = report.dsc_public_key_algorithm_name === "rsaEncryption"
  const hash = (report.sod_signature_algorithm_hash || "").toLowerCase() // e.g. "sha256"
  const algPathGuess = isRSA ? `rsa/pkcs/2048/${hash}` : null
  report.selected_circuit_family = "sig-check/id-data"
  report.selected_circuit_tbs_bucket = `tbs_${report.tbs_max_len}`
  report.selected_circuit_path_guess = algPathGuess
    ? `src/noir/bin/sig-check/id-data/tbs_${report.tbs_max_len}/${algPathGuess}`
    : "no automatic guess (non-RSA path) — inspect signature algorithm manually"

  console.log(JSON.stringify(report, null, 2))
}

main()
