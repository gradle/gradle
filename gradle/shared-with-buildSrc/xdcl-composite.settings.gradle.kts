// Shadow mode: source the org.xdcl libraries straight from an xdcl checkout — no publish
// round-trip, no SNAPSHOT staleness. Substitution matches the distribution.versions.toml
// coordinates by group:name and is the ONLY source of org.xdcl artifacts, so a substitution gap
// fails resolution loudly instead of picking up stale jars.
//
// Applied from BOTH the root settings and build-logic's settings, so the composite is declared in
// exactly one place. The checkout is located in this order:
//
//   1. a git-ignored `xdcl-checkout.txt` at the repository root, holding a path (relative to the
//      root, or absolute) — for building against an arbitrary checkout;
//   2. the in-repo `xdcl/` git submodule, which pins the exact org.xdcl revision this branch
//      builds against and makes the branch self-contained (`git submodule update --init`);
//   3. the sibling `../xdcl`, the historical convention (see the build-from-source docs).
//
// (2) is what CI uses: it needs no checkout layout of its own, so every existing build
// configuration builds this branch unchanged. (3) keeps working for anyone already set up that way
// — and, unlike (2), survives `git worktree add`, where `../xdcl` points into the worktrees
// directory instead.
val repoRoot = generateSequence(settings.rootDir) { it.parentFile }
    .first { File(it, "gradle/shared-with-buildSrc").isDirectory }

fun candidate(path: String): File =
    File(path).takeIf { it.isAbsolute } ?: File(repoRoot, path)

fun File.isXdclCheckout(): Boolean = File(this, "settings.gradle.kts").isFile

val overrideFile = File(repoRoot, "xdcl-checkout.txt")
val override = overrideFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

val xdclDir = if (override != null) {
    candidate(override).also {
        require(it.isXdclCheckout()) {
            "xdcl checkout not found at $it (from $overrideFile); " +
                "point xdcl-checkout.txt at your xdcl checkout (path relative to $repoRoot, or absolute)"
        }
    }
} else {
    val submodule = candidate("xdcl")
    val sibling = candidate("../xdcl")
    listOf(submodule, sibling).firstOrNull { it.isXdclCheckout() }
        ?: throw GradleException(
            "xdcl checkout not found — looked at the `xdcl/` submodule ($submodule) and the sibling " +
                "checkout ($sibling). Run `git submodule update --init xdcl`, or point a git-ignored " +
                "$overrideFile at your own checkout (path relative to $repoRoot, or absolute)."
        )
}

includeBuild(xdclDir.canonicalFile)
