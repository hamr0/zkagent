const fs = require("fs")
const path = require("path")
const {
  Binary, PassportReader, getTBSMaxLen, isIDSupported,
  getDSCSignatureAlgorithmHashAlgorithm, getSodSignatureAlgorithmHashAlgorithm,
} = require("@zkpassport/utils")

const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
const docId = process.argv[2]
const dg1Bytes = fs.readFileSync(path.join(FIXTURES, `${docId}.dg1`))
const sodBytes = fs.readFileSync(path.join(FIXTURES, `${docId}.sod`))

const dg1 = Binary.from(dg1Bytes)
const sod = Binary.from(sodBytes)
const reader = new PassportReader()
const report = { doc_id: docId, dg1_byte_length: dg1Bytes.length, sod_byte_length: sodBytes.length }
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
const cert = vm.sod.certificate
report.dsc_signature_algorithm_hash = getDSCSignatureAlgorithmHashAlgorithm(vm)
report.sod_signature_algorithm_hash = getSodSignatureAlgorithmHashAlgorithm(vm)
report.tbs_certificate_signature_algorithm_name = cert.tbs.signatureAlgorithm.name
report.tbs_max_len = getTBSMaxLen(vm)
report.tbs_actual_byte_length = cert.tbs.bytes ? cert.tbs.bytes.length : undefined
report.is_id_supported = isIDSupported(vm)
const spki = cert.tbs.subjectPublicKeyInfo
report.dsc_public_key_algorithm_name = spki.signatureAlgorithm.name
report.dsc_public_key_der_byte_length = spki.subjectPublicKey.bytes.length
const isRSA = report.dsc_public_key_algorithm_name === "rsaEncryption"
const hash = (report.sod_signature_algorithm_hash || "").toLowerCase()
report.selected_circuit_path_guess = isRSA
  ? `src/noir/bin/sig-check/id-data/tbs_${report.tbs_max_len}/rsa/pkcs/2048/${hash}`
  : null
console.log(JSON.stringify(report, null, 2))
