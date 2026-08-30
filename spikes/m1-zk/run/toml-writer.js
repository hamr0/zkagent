// Minimal recursive TOML writer for the shapes @zkpassport/utils circuit-input
// builders return: numbers, strings (hex/decimal field elements), arrays of
// numbers/strings, and nested plain objects (structs like SaltedValue<T> or
// OPRFProof). Good enough for Prover.toml generation only.
const fs = require("fs")

function scalarToToml(v) {
  if (typeof v === "number") return String(v)
  if (typeof v === "string") return `"${v}"`
  throw new Error(`unsupported scalar type: ${typeof v}`)
}

function arrayToToml(arr) {
  return "[" + arr.map((x) => (typeof x === "number" ? String(x) : `"${x}"`)).join(", ") + "]"
}

function writeProverToml(inputs, outPath) {
  const topLevelScalars = []
  const tables = []

  function walk(prefix, obj) {
    const scalarsHere = []
    const nestedTables = []
    for (const [k, v] of Object.entries(obj)) {
      if (Array.isArray(v)) {
        scalarsHere.push(`${k} = ${arrayToToml(v)}`)
      } else if (typeof v === "object" && v !== null) {
        nestedTables.push([k, v])
      } else {
        scalarsHere.push(`${k} = ${scalarToToml(v)}`)
      }
    }
    if (prefix) {
      tables.push(`[${prefix}]\n` + scalarsHere.join("\n"))
    } else {
      topLevelScalars.push(...scalarsHere)
    }
    for (const [k, v] of nestedTables) {
      walk(prefix ? `${prefix}.${k}` : k, v)
    }
  }

  walk("", inputs)
  const out = [topLevelScalars.join("\n"), ...tables].filter(Boolean).join("\n\n") + "\n"
  fs.writeFileSync(outPath, out)
}

module.exports = { writeProverToml }
