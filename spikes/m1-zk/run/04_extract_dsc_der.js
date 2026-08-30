// Extracts the DSC certificate (DER) embedded in a real SOD fixture, re-encoded
// via @peculiar/asn1-schema from the parsed CMS structure. DSC certs identify
// the issuing government's document-signer key, not the passport holder --
// no PII fields are involved (subject is the issuing authority, not a person).
const fs = require("fs")
const path = require("path")
const { AsnParser, AsnSerializer } = require("@peculiar/asn1-schema")
const { ContentInfo, SignedData } = require("@peculiar/asn1-cms")
const { Certificate } = require("@peculiar/asn1-x509")

function stripEfSodWrapper(sodBytes) {
  // EF.SOD = [APPLICATION 23] wrapper (tag 0x77) around the ContentInfo DER
  const lenByte = sodBytes[1]
  const offset = lenByte & 0x80 ? 2 + (lenByte & 0x7f) : 2
  return sodBytes.slice(offset)
}

const docId = process.argv[2]
const outPath = process.argv[3]
const FIXTURES = path.join(__dirname, "..", "fixtures", "real")
const sodBytes = fs.readFileSync(path.join(FIXTURES, `${docId}.sod`))
const contentInfoDer = stripEfSodWrapper(sodBytes)
const ci = AsnParser.parse(contentInfoDer, ContentInfo)
const sd = AsnParser.parse(ci.content, SignedData)
if (!sd.certificates || sd.certificates.length !== 1) {
  console.error(JSON.stringify({ ok: false, num_certs: sd.certificates ? sd.certificates.length : 0 }))
  process.exit(1)
}
const certChoice = sd.certificates[0]
const cert = certChoice.certificate // CertificateChoices -> certificate
const der = Buffer.from(AsnSerializer.serialize(cert))
fs.writeFileSync(outPath, der)
console.log(JSON.stringify({ ok: true, doc_id: docId, dsc_der_bytes: der.length, written_to: outPath }))
