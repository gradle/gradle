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
 * Must stay in sync with Gradleception: the same fixed timestamp and the same `gradleception-` qualifier
 * prefix mean an identical source tree yields an identical version string in both build types, so they
 * share build cache entries instead of each populating its own namespace.
 */
private const val DOGFOOD_TIMESTAMP = "19800101010101+0000"
private const val DOGFOOD_INSTALL_DIR = "dogfood-for-hash"

/**
 * Smoke tests run against the Gradle distribution this build produces, so that distribution's version
 * decides what they can reuse - and by default it carried a wall-clock build timestamp, unique per CI run.
 *
 * Two consequences, both of which this build works around by giving the distribution a version derived from
 * its own contents (a fixed timestamp plus the MD5 of a throwaway install, exactly as [Gradleception] does):
 *
 * 1. `DistributionTest.gradleDistribution` is a `@get:Nested` task input, so a distribution that differs on
 *    every run made every `SmokeTest` task's inputs differ on every run. Despite `SmokeTest` being declared
 *    `@CacheableTask`, no smoke test task ever hit the cache. Now an unchanged distribution and unchanged
 *    test code mean the whole task is served from the cache.
 *
 * 2. For the smoke tests whose nested builds are gradle/gradle itself (`gradleBuildSmokeTest`), the build
 *    cache key of every cacheable task embeds the version of the *executing* Gradle:
 *    `RegistryAwareClassLoaderHierarchyHasher` identifies the Gradle runtime classloaders as
 *    `"runtime:$gradleVersion"`, which feeds each task's implementation hash. A per-run version gave each
 *    run a private cache namespace, so those nested builds recompiled Gradle from scratch - 5.3% task
 *    avoidance, 12min for a single test method.
 *
 * The version has to be derived from the distribution's contents rather than simply pinned to a constant.
 * These tests share one integration-test Gradle user home which the artifact cache restores across builds,
 * so a constant version could let its version-scoped caches (`caches/<version>/kotlin-dsl/scripts`) serve
 * content compiled against a different distribution. Hashing the contents keeps distinct distributions on
 * distinct versions.
 */
class SmokeTests(
    model: CIBuildModel,
    stage: Stage,
    testJava: JvmCategory,
    id: String,
    task: String = "smokeTest",
    splitNumber: Int = 1,
    flakyTestStrategy: FlakyTestStrategy,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
        val suffix = if (flakyTestStrategy == FlakyTestStrategy.ONLY) "_FlakyTestQuarantine" else ""
        id("${model.projectId}_SmokeTest_$id$suffix")
        name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.toCapitalized()} Linux$suffix"
        description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

        if (flakyTestStrategy != FlakyTestStrategy.ONLY) {
            // No need to split in FlakyTestQuarantine
            tcParallelTests(splitNumber)
        }

        params {
            // Override the default commit id so the build steps produce a reproducible distribution.
            param("env.BUILD_COMMIT_ID", "HEAD")
        }

        val reproducibleDistributionParameters = listOf("-PignoreIncomingBuildReceipt=true", "-PbuildTimestamp=$DOGFOOD_TIMESTAMP")

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
                    ) + reproducibleDistributionParameters
                ).joinToString(" "),
            preSteps = {
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
                                ) + reproducibleDistributionParameters + functionalTestParameters(Os.LINUX)
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
            },
        )
    })
