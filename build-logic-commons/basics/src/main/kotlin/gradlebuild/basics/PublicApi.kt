package gradlebuild.basics

import java.util.regex.Pattern


/**
 * This is the definition of what constitutes the Gradle public API.
 *
 * A type is part of the Gradle public API if and only if its FQCN matches {@link #includes} and does not match {@link #excludes}.
 */
// NOTE: If you update this, please also change .idea/scopes/Gradle_public_API.xml
object PublicApi {
    val includes = listOf(
        "org/gradle/*",
        "org/gradle/api/**",
        "org/gradle/authentication/**",
        "org/gradle/build/**",
        "org/gradle/buildconfiguration/**",
        "org/gradle/buildinit/**",
        "org/gradle/caching/**",
        "org/gradle/concurrent/**",
        "org/gradle/deployment/**",
        "org/gradle/external/javadoc/**",
        "org/gradle/ide/**",
        "org/gradle/ivy/**",
        "org/gradle/jvm/**",
        "org/gradle/language/**",
        "org/gradle/maven/**",
        "org/gradle/nativeplatform/**",
        "org/gradle/normalization/**",
        "org/gradle/platform/**",
        "org/gradle/plugin/devel/**",
        "org/gradle/plugin/use/*",
        "org/gradle/plugin/management/*",
        "org/gradle/plugins/**",
        "org/gradle/process/**",
        "org/gradle/testfixtures/**",
        "org/gradle/testing/jacoco/**",
        "org/gradle/tooling/**",
        "org/gradle/swiftpm/**",
        "org/gradle/model/**",
        "org/gradle/testkit/**",
        "org/gradle/testing/**",
        "org/gradle/vcs/**",
        "org/gradle/work/**",
        "org/gradle/workers/**",
        "org/gradle/util/**", // contains Path that clashes with `org.gradle.api.model.Path` imported above. This line should not appear before "org/gradle/api/**"
    )

    val excludes = listOf("**/internal/**")

    private val includePackagePatterns: List<Pattern> by lazy {
        includes.map {
            if (it.endsWith("/**")) {
                Pattern.compile(it.substring(0, it.length - 3).replace("/", "\\.") + "(\\..+)?")
            } else {
                Pattern.compile(it.substring(0, it.length - 2).replace("/", "\\.") + "\\.[^.]+")
            }
        }
    }

    private val excludePackagePatterns = listOf(Pattern.compile(".+\\.internal(\\..+)?"))

    fun isPublicApiPackage(packageName: String) =
        includePackagePatterns.any { it.matcher(packageName).matches() } &&
            !excludePackagePatterns.any { it.matcher(packageName).matches() }
}
