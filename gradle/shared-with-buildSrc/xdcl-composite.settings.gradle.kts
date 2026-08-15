// Local-only (shadow mode): source the org.xdcl libraries straight from a sibling xdcl checkout —
// no publish round-trip, no SNAPSHOT staleness. Substitution matches the
// distribution.versions.toml coordinates by group:name and is the ONLY source of org.xdcl
// artifacts, so a substitution gap fails resolution loudly instead of picking up stale jars.
//
// Applied from BOTH the root settings and build-logic's settings, so the composite is declared in
// exactly one place. The checkout location defaults to the sibling `../xdcl`; to build against a
// differently-named checkout, put its path (relative to this repository's root, or absolute) in a
// git-ignored `xdcl-checkout.txt` at the repository root — one file, both builds follow.
val repoRoot = generateSequence(settings.rootDir) { it.parentFile }
    .first { File(it, "gradle/shared-with-buildSrc").isDirectory }
val overrideFile = File(repoRoot, "xdcl-checkout.txt")
val checkout = overrideFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() } ?: "../xdcl"
val xdclDir = File(checkout).takeIf { it.isAbsolute } ?: File(repoRoot, checkout)
require(File(xdclDir, "settings.gradle.kts").isFile) {
    "xdcl checkout not found at $xdclDir (from ${if (overrideFile.isFile) overrideFile else "the ../xdcl default"}); " +
        "point xdcl-checkout.txt at your xdcl checkout (path relative to $repoRoot, or absolute)"
}
includeBuild(xdclDir.canonicalFile)
