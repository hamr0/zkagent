// Minimal DER walker — stdlib only, no ASN.1 library.
// Supports exactly the constructs needed to decode a KeyDescription
// (Android key attestation extension): SEQUENCE, SET, INTEGER,
// ENUMERATED, BOOLEAN, OCTET STRING, OBJECT IDENTIFIER, and
// explicit context-specific tags ([n] EXPLICIT ...).
//
// This is NOT a general-purpose ASN.1/DER parser. It does not handle
// indefinite lengths (BER), does not handle non-EXPLICIT context tags,
// and assumes well-formed DER (as produced by a real X.509 stack).

export const TAG_CLASS = { UNIVERSAL: 0, APPLICATION: 1, CONTEXT: 2, PRIVATE: 3 };

// Parse one TLV (tag-length-value) at `offset` in `buf`.
// Returns { tagClass, isConstructed, tagNumber, headerLen, length, valueStart, valueEnd, nextOffset }
export function readTLV(buf, offset) {
  if (offset >= buf.length) throw new Error(`DER: read past end of buffer at offset ${offset}`);
  const first = buf[offset];
  const tagClass = (first >> 6) & 0x03;
  const isConstructed = (first & 0x20) !== 0;
  let tagNumber = first & 0x1f;
  let pos = offset + 1;

  if (tagNumber === 0x1f) {
    // high-tag-number form (multi-byte tag) — not needed for KeyDescription, but handle it
    tagNumber = 0;
    let b;
    do {
      if (pos >= buf.length) throw new Error('DER: truncated high-tag-number');
      b = buf[pos++];
      tagNumber = (tagNumber << 7) | (b & 0x7f);
    } while (b & 0x80);
  }

  if (pos >= buf.length) throw new Error('DER: truncated length');
  let length;
  const lenByte = buf[pos++];
  if ((lenByte & 0x80) === 0) {
    length = lenByte; // short form
  } else {
    const numLenBytes = lenByte & 0x7f;
    if (numLenBytes === 0) throw new Error('DER: indefinite length not supported');
    if (numLenBytes > 6) throw new Error('DER: length field too large');
    length = 0;
    for (let i = 0; i < numLenBytes; i++) {
      if (pos >= buf.length) throw new Error('DER: truncated long-form length');
      length = length * 256 + buf[pos++];
    }
  }

  const valueStart = pos;
  const valueEnd = valueStart + length;
  if (valueEnd > buf.length) throw new Error(`DER: value extends past buffer end (tag=${tagNumber}, len=${length})`);

  return {
    tagClass,
    isConstructed,
    tagNumber,
    headerLen: valueStart - offset,
    length,
    valueStart,
    valueEnd,
    nextOffset: valueEnd,
  };
}

// Walk a constructed value's children as a flat array of TLVs.
export function readChildren(buf, valueStart, valueEnd) {
  const out = [];
  let pos = valueStart;
  while (pos < valueEnd) {
    const tlv = readTLV(buf, pos);
    out.push(tlv);
    pos = tlv.nextOffset;
  }
  return out;
}

export function readSequence(buf, offset) {
  const tlv = readTLV(buf, offset);
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 16 || !tlv.isConstructed) {
    throw new Error(`DER: expected SEQUENCE at offset ${offset}, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  return { ...tlv, children: readChildren(buf, tlv.valueStart, tlv.valueEnd) };
}

export function readSet(buf, offset) {
  const tlv = readTLV(buf, offset);
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 17 || !tlv.isConstructed) {
    throw new Error(`DER: expected SET at offset ${offset}, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  return { ...tlv, children: readChildren(buf, tlv.valueStart, tlv.valueEnd) };
}

export function readIntegerBigInt(buf, tlv) {
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 2) {
    throw new Error(`DER: expected INTEGER, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  const bytes = buf.subarray(tlv.valueStart, tlv.valueEnd);
  if (bytes.length === 0) return 0n;
  let negative = (bytes[0] & 0x80) !== 0;
  let hex = Buffer.from(bytes).toString('hex');
  let val = BigInt('0x' + hex);
  if (negative) {
    val -= (1n << BigInt(bytes.length * 8));
  }
  return val;
}

export function readEnumerated(buf, tlv) {
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 10) {
    throw new Error(`DER: expected ENUMERATED, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  return Number(readIntegerBigInt(buf, { ...tlv, tagNumber: 2, tagClass: TAG_CLASS.UNIVERSAL }));
}

export function readBoolean(buf, tlv) {
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 1) {
    throw new Error(`DER: expected BOOLEAN, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  return buf[tlv.valueStart] !== 0;
}

export function readOctetString(buf, tlv) {
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 4) {
    throw new Error(`DER: expected OCTET STRING, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  return Buffer.from(buf.subarray(tlv.valueStart, tlv.valueEnd));
}

export function readOID(buf, tlv) {
  if (tlv.tagClass !== TAG_CLASS.UNIVERSAL || tlv.tagNumber !== 6) {
    throw new Error(`DER: expected OBJECT IDENTIFIER, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  const bytes = buf.subarray(tlv.valueStart, tlv.valueEnd);
  const parts = [];
  const first = bytes[0];
  parts.push(Math.floor(first / 40));
  parts.push(first % 40);
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

// A context-specific EXPLICIT [n] tag wraps exactly one inner TLV.
// Returns the inner TLV (the thing that was "explicitly" tagged).
export function readExplicit(buf, tlv) {
  if (tlv.tagClass !== TAG_CLASS.CONTEXT || !tlv.isConstructed) {
    throw new Error(`DER: expected constructed context tag, got class=${tlv.tagClass} tag=${tlv.tagNumber}`);
  }
  const inner = readTLV(buf, tlv.valueStart);
  return inner;
}

// Find a context-specific tag by number among a list of sibling TLVs (e.g. AuthorizationList children).
export function findContextTag(children, tagNumber) {
  return children.find((c) => c.tagClass === TAG_CLASS.CONTEXT && c.tagNumber === tagNumber);
}
