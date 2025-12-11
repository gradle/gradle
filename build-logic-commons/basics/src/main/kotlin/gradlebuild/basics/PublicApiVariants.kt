package gradlebuild.basics


/**
 * Naming of the capability-differentiated variants of the `:public-api` project.
 *
 * NOTE: [LEGACY_MODULE_NAME] is also referenced at runtime by the Kotlin DSL script classpath
 * provider, which looks jar up in the distribution's `ModuleRegistry` by that exact name.
 * That consumer lives on a different classpath and cannot share this constant,
 * so it hardcodes the same string. Keep the two in sync.
 */
object PublicApiVariants {
    const val INTERNAL_SUFFIX = "-internal"
    const val LEGACY_SUFFIX = "-legacy"
    const val LEGACY_MODULE_NAME = "gradle-public-api$LEGACY_SUFFIX"
}
