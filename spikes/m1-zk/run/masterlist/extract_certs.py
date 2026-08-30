"""Minimal DER TLV walker to pull raw Certificate blobs out of an ICAO
CscaMasterList structure: SEQUENCE { version INTEGER, certList SET OF Certificate }.
Stdlib-only (no asn1crypto/pyasn1 dependency) per the project's dependency
hierarchy (vanilla/stdlib before reaching for a library) -- this is well under
100 lines and touches no cryptographic logic, only DER length parsing.
"""
import sys


def read_tlv(data, offset):
    tag = data[offset]
    length_byte = data[offset + 1]
    if length_byte & 0x80:
        num_len_bytes = length_byte & 0x7F
        length = int.from_bytes(data[offset + 2 : offset + 2 + num_len_bytes], "big")
        header_len = 2 + num_len_bytes
    else:
        length = length_byte
        header_len = 2
    total_len = header_len + length
    return tag, data[offset : offset + total_len], data[offset + header_len : offset + total_len], total_len


def extract_certs(path):
    with open(path, "rb") as f:
        data = f.read()
    tag, whole, outer_seq_content, _ = read_tlv(data, 0)
    assert tag == 0x30, f"expected top-level SEQUENCE, got tag 0x{tag:02x}"
    # first element: version INTEGER
    _, _, _, ver_len = read_tlv(outer_seq_content, 0)
    # second element: certList SET OF Certificate
    set_tag, set_whole, set_content, _ = read_tlv(outer_seq_content, ver_len)
    assert set_tag == 0x31, f"expected SET, got tag 0x{set_tag:02x}"
    certs = []
    off = 0
    while off < len(set_content):
        cert_tag, cert_whole, _, cert_len = read_tlv(set_content, off)
        assert cert_tag == 0x30, f"expected Certificate SEQUENCE, got tag 0x{cert_tag:02x} at offset {off}"
        certs.append(cert_whole)
        off += cert_len
    return certs


if __name__ == "__main__":
    certs = extract_certs(sys.argv[1])
    print(f"extracted {len(certs)} certificates (DER)", file=sys.stderr)
    out_dir = sys.argv[2]
    import os

    os.makedirs(out_dir, exist_ok=True)
    for i, c in enumerate(certs):
        with open(os.path.join(out_dir, f"csca_{i:04d}.der"), "wb") as f:
            f.write(c)
