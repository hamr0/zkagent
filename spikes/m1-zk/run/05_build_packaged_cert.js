// Port of vendor/zkpassport-circuits/src/ts/test-helper.ts's
// convertPemToPackagedCertificateV1, using only @zkpassport/utils + @peculiar
// (already deps here). Builds a PackagedCertificate (v1 schema) from a PEM.
const fs = require("fs")
const { AsnParser } = require("@peculiar/asn1-schema")
const { Certificate } = require("@peculiar/asn1-x509")
const {
  packLeBytesAndHashPoseidon2,
  getCertificateIssuerCountry,
  getSignatureAlgorithmType,
  getRSAInfo,
  getECDSAInfo,
  getKeySize,
  getAuthorityKeyId,
  getSubjectKeyId,
  getPrivateKeyUsagePeriod,
  countryCodeAlpha2ToAlpha3,
  OIDS_TO_PUBKEY_TYPE,
} = require("@zkpassport/utils")

function pemToDer(pem) {
  const b64 = pem.replace(/-----(BEGIN|END) CERTIFICATE-----/g, "").replace(/\s+/g, "")
  return new Uint8Array(Buffer.from(b64, "base64"))
}

function derToPem(der) {
  const b64 = Buffer.from(der).toString("base64")
  const lines = b64.match(/.{1,64}/g).join("\n")
  return `-----BEGIN CERTIFICATE-----\n${lines}\n-----END CERTIFICATE-----\n`
}

async function convertDerToPackagedCertificateV1(der) {
  const x509 = AsnParser.parse(der, Certificate)
  const fingerprintBig = await packLeBytesAndHashPoseidon2(der)
  const fingerprint = `0x${fingerprintBig.toString(16).padStart(64, "0")}`

  const validity = x509.tbsCertificate.validity
  const notBefore = Math.floor(validity.notBefore.getTime().getTime() / 1000)
  const notAfter = Math.floor(validity.notAfter.getTime().getTime() / 1000)

  const countryAlpha2 = getCertificateIssuerCountry(x509)
  if (!countryAlpha2 || countryAlpha2.length !== 2)
    throw new Error(`Invalid country code on cert: ${countryAlpha2}`)
  const country = countryCodeAlpha2ToAlpha3(countryAlpha2)

  const publicKeyOID = x509.tbsCertificate.subjectPublicKeyInfo.algorithm.algorithm
  const publicKeyType = OIDS_TO_PUBKEY_TYPE[publicKeyOID] ?? publicKeyOID

  const common = {
    country,
    signature_algorithm: getSignatureAlgorithmType(x509),
    validity: { not_before: notBefore, not_after: notAfter },
    authority_key_identifier: getAuthorityKeyId(x509),
    subject_key_identifier: getSubjectKeyId(x509),
    private_key_usage_period: getPrivateKeyUsagePeriod(x509),
    fingerprint,
  }

  if (publicKeyType === "rsaEncryption" || publicKeyType === "rsassa-pss") {
    const rsa = getRSAInfo(x509.tbsCertificate.subjectPublicKeyInfo)
    return { ...common, public_key: {
      type: "RSA",
      modulus: `0x${rsa.modulus.toString(16)}`,
      exponent: Number(rsa.exponent),
      key_size: getKeySize(x509.tbsCertificate.subjectPublicKeyInfo),
    } }
  }
  if (publicKeyType === "ecPublicKey") {
    const ec = getECDSAInfo(x509.tbsCertificate.subjectPublicKeyInfo)
    const half = ec.publicKey.length / 2
    return { ...common, public_key: {
      type: "EC",
      curve: ec.curve,
      key_size: ec.keySize,
      public_key_x: `0x${Buffer.from(ec.publicKey.slice(1, half + 1)).toString("hex")}`,
      public_key_y: `0x${Buffer.from(ec.publicKey.slice(half + 1)).toString("hex")}`,
    } }
  }
  throw new Error(`Unsupported public key type: ${publicKeyType}`)
}

module.exports = { convertDerToPackagedCertificateV1, pemToDer, derToPem }
