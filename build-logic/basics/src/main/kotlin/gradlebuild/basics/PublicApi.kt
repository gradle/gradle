package gradlebuild.basics


/**
 * This is the definition of what constitutes the Gradle public API.
 *
 * A type is part of the Gradle public API if and only if its FQCN matches {@link #includes} and does not match {@link #excludes}.
 */
// NOTE: If you update this, please also change .idea/scopes/Gradle_public_API.xml
// and also change `avoiding_gradle_internal_apis` section in `src/docs/userguide/authoring-builds/authoring_maintainable_build_scripts.adoc`
object PublicApi {
    val includes = listOf(
        "org/gradle/*",
        "org/gradle/api/**",
        "org/gradle/authentication/**",
        "org/gradle/build/**",
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
        "org/gradle/play/**",
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
}
