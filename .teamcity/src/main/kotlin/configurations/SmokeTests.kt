package configurations

import common.FlakyTestStrategy
import common.JvmCategory
import common.Os
import common.buildScanTagParam
import common.buildToolGradleParameters
import common.functionalTestParameters
import common.getBuildScanCustomValueParam
import common.gradleWrapper
import common.toCapitalized
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

/*
 * The values below must stay in sync with Gradleception: using the same fixed timestamp and the same
 * `gradleception-` qualifier prefix means an identical source tree yields an identical version string in
 * both build types, so they share build cache entries instead of each populating its own namespace.
 */
private const val DOGFOOD_TIMESTAMP = "19800101010101+0000"
private const val DOGFOOD_INSTALL_DIR = "dogfood-for-hash"

class SmokeTests(
    model: CIBuildModel,
    stage: Stage,
    testJava: JvmCategory,
    id: String,
    task: String = "smokeTest",
    splitNumber: Int = 1,
    flakyTestStrategy: FlakyTestStrategy,
    /**
     * Give the distribution under test a version derived from its own contents instead of a wall-clock
     * timestamp. Only worth it for the smoke tests that run nested Gradle builds (`gradleBuildSmokeTest`).
     *
     * Those nested builds execute with the distribution this build produces, and the build cache key of
     * every cacheable task embeds the version of the executing Gradle: `RegistryAwareClassLoaderHierarchyHasher`
     * identifies the Gradle runtime classloaders as `"runtime:$gradleVersion"`, which feeds each task's
     * implementation hash. With the default wall-clock timestamp that version is unique per CI run, so every
     * run gets a private cache namespace and the nested builds recompile Gradle from scratch - measured at
     * ~5% task avoidance, ~12min for a single test method.
     *
     * Gradleception already solves this (see [Gradleception]): build a throwaway distribution with a fixed
     * timestamp, hash it, then use that hash as the version qualifier. The version then depends only on the
     * distribution's contents, so runs whose sources produce identical distributions reuse each other's cache
     * entries, while genuinely different distributions still get distinct versions - which matters because the
     * smoke tests share (and the artifact cache restores) one integration-test Gradle user home, whose
     * version-scoped caches would otherwise be at risk of serving content from a different distribution.
     * Gradleception measures ~87% task avoidance with this scheme.
     */
    deterministicDistributionVersion: Boolean = false,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
        val suffix = if (flakyTestStrategy == FlakyTestStrategy.ONLY) "_FlakyTestQuarantine" else ""
        id("${model.projectId}_SmokeTest_$id$suffix")
        name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.toCapitalized()} Linux$suffix"
        description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

        if (flakyTestStrategy != FlakyTestStrategy.ONLY) {
            // No need to split in FlakyTestQuarantine
            tcParallelTests(splitNumber)
        }

        if (deterministicDistributionVersion) {
            params {
                // Override the default commit id so the build steps produce a reproducible distribution.
                param("env.BUILD_COMMIT_ID", "HEAD")
            }
        }

        val deterministicVersionParameters =
            if (deterministicDistributionVersion) {
                listOf("-PignoreIncomingBuildReceipt=true", "-PbuildTimestamp=$DOGFOOD_TIMESTAMP")
            } else {
                emptyList()
            }

        applyTestDefaults(
            model,
            this,
            ":smoke-test:$task",
            timeout = if (flakyTestStrategy == FlakyTestStrategy.ONLY) 30 else 120,
            extraParameters =
                (
                    listOf(
                        stage.getBuildScanCustomValueParam(),
                        buildScanTagParam("SmokeTests"),
                        "-PtestJavaVersion=${testJava.version.major}",
                        "-PtestJavaVendor=${testJava.vendor.name.lowercase()}",
                        "-PflakyTests=$flakyTestStrategy",
                    ) + deterministicVersionParameters
                ).joinToString(" "),
            preSteps = {
                if (deterministicDistributionVersion) {
                    // Runs with the wrapper, i.e. with the released Gradle the rest of CI uses, so this step
                    // itself hits the remote cache the same way CompileAll does.
                    gradleWrapper {
                        name = "BUILD_DISTRIBUTION_TO_HASH"
                        tasks = "clean :distributions-full:install"
                        gradleParams =
                            (
                                buildToolGradleParameters() +
                                    listOf(
                                        "-Pgradle_installPath=$DOGFOOD_INSTALL_DIR",
                                        buildScanTagParam("SmokeTests"),
                                    ) + deterministicVersionParameters + functionalTestParameters(Os.LINUX)
                            ).joinToString(" ")
                    }
                    script {
                        name = "CALCULATE_MD5_VERSION_FOR_DISTRIBUTION_UNDER_TEST"
                        workingDir = "%teamcity.build.checkoutDir%/$DOGFOOD_INSTALL_DIR"
                        scriptContent =
                            """
                            set -x
                            MD5=`find . -type f | sort | xargs md5sum | md5sum | awk '{ print $1 }'`
                            echo "##teamcity[setParameter name='env.ORG_GRADLE_PROJECT_versionQualifier' value='gradleception-${'$'}MD5']"
                            """.trimIndent()
                    }
                }
            },
        )
    })
