// Shared chain-decoding helpers used by both verify-attestation.mjs and diff-chains.mjs.
import { readFileSync } from 'node:fs';
import { X509Certificate } from 'node:crypto';
import { readTLV, readOctetString, TAG_CLASS } from './der.mjs';
import { decodeKeyDescription, ATTESTATION_EXTENSION_OID } from './key-description.mjs';

export function splitPemCerts(pem) {
  const matches = pem.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g);
  if (!matches) throw new Error('No PEM certificates found in input');
  return matches;
}

export function parseChainFromPem(pem) {
  return splitPemCerts(pem).map((block, i) => {
    try {
      return new X509Certificate(block);
    } catch (e) {
      throw new Error(`Failed to parse cert at chain position ${i}: ${e.message}`);
    }
  });
}

function readOidFromTlv(buf, tlv) {
  const bytes = buf.subarray(tlv.valueStart, tlv.valueEnd);
  const parts = [Math.floor(bytes[0] / 40), bytes[0] % 40];
  let value = 0n;
  for (let i = 1; i < bytes.length; i++) {
    value = (value << 7n) | BigInt(bytes[i] & 0x7f);
    if ((bytes[i] & 0x80) === 0) {
      parts.push(value.toString());
      value = 0n;
    }
  }
  return parts.join('.');
}

// Locate + return the raw DER value bytes of the attestation extension in a cert's DER.
export function findAttestationExtensionValue(certDer) {
  const certSeq = readTLV(certDer, 0);
  const tbs = readTLV(certDer, certSeq.valueStart);
  let pos = tbs.valueStart;
  let extensionsTag = null;
  while (pos < tbs.valueEnd) {
    const t = readTLV(certDer, pos);
    if (t.tagClass === TAG_CLASS.CONTEXT && t.tagNumber === 3) extensionsTag = t;
    pos = t.nextOffset;
  }
  if (!extensionsTag) return null;
  const extensionsSeq = readTLV(certDer, extensionsTag.valueStart);
  let extPos = extensionsSeq.valueStart;
  while (extPos < extensionsSeq.valueEnd) {
    const ext = readTLV(certDer, extPos);
    const oidTlv = readTLV(certDer, ext.valueStart);
    const oid = readOidFromTlv(certDer, oidTlv);
    if (oid === ATTESTATION_EXTENSION_OID) {
      let p = oidTlv.nextOffset;
      let valueTlv = readTLV(certDer, p);
      if (valueTlv.tagClass === TAG_CLASS.UNIVERSAL && valueTlv.tagNumber === 1) {
        p = valueTlv.nextOffset;
        valueTlv = readTLV(certDer, p);
      }
      return readOctetString(certDer, valueTlv);
    }
    extPos = ext.nextOffset;
  }
  return null;
}

export function describeCert(c, i) {
  return {
    index: i,
    subject: c.subject,
    issuer: c.issuer,
    serialNumber: c.serialNumber,
    validFrom: c.validFrom,
    validTo: c.validTo,
  };
}

export function decodeChainFromPem(pem) {
  const certs = parseChainFromPem(pem);
  const chain = certs.map(describeCert);
  let extension = null;
  try {
    const extValue = findAttestationExtensionValue(certs[0].raw);
    extension = extValue
      ? decodeKeyDescription(extValue)
      : { error: `attestation extension ${ATTESTATION_EXTENSION_OID} not found in leaf` };
  } catch (e) {
    extension = { error: `failed to decode attestation extension: ${e.message}` };
  }
  return { certs, chain, extension };
}

export function decodeChainFromFile(path) {
  return decodeChainFromPem(readFileSync(path, 'utf8'));
}
