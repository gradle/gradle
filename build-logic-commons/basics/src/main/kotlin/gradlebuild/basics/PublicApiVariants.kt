package gradlebuild.basics


/**
 * Capability differentiated variants of the `:public-api` project.
 *
 * These are ABI jars: stubs with no method bodies, produced by the API extractor, meant to compile
 * against rather than run. Both variants hold the same ABI (public API plus internals) and differ
 * only in dependencies. [INTERNAL_SUFFIX] is the published `gradleApi()` surface. [LEGACY_SUFFIX]
 * mirrors the historical `gradleApi()` runtime classpath and ships in the distribution's `lib/api/`.
 *
 * "legacy" is about content, not deprecation: the jar still bundles internals, as `gradleApi()`
 * always has. The goal is a future `gradle-public-api` variant that contains only the public API.
 *
 * NOTE: [LEGACY_MODULE_NAME] is also hardcoded at runtime by the Kotlin DSL script classpath
 * provider (separate classpath, cannot share this constant), so keep the two in sync.
 */
object PublicApiVariants {
    const val INTERNAL_SUFFIX = "-internal"
    const val LEGACY_SUFFIX = "-legacy"
    const val LEGACY_MODULE_NAME = "gradle-public-api$LEGACY_SUFFIX"
}
