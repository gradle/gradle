package configurations

import common.buildToolGradleParameters
import common.customGradle
import common.dependsOn
import common.gradleWrapper
import common.requiresNotEc2Agent
import common.requiresNotSharedHost
import common.skipConditionally
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.Stage

/**
 * Build a Gradle distribution (dogfood-first) and use this distribution to build a distribution again (dogfood-second).
 * Use `dogfood-second` to run `test sanityCheck`.
 */
class Gradleception(model: CIBuildModel, stage: Stage, bundleGroovy4: Boolean = false) : BaseGradleBuildType(stage = stage, init = {
    if (bundleGroovy4) id("${model.projectId}_GradleceptionWithGroovy4") else id("${model.projectId}_Gradleception")
    name = if (bundleGroovy4) "Gradleception - Groovy 4.x Java8 Linux" else "Gradleception - Java8 Linux"
    description = "Builds Gradle with the version of Gradle which is currently under development (twice)" + if (bundleGroovy4) " - bundling Groovy 4" else ""

    requirements {
        // Gradleception is a heavy build which runs ~40m on EC2 agents but only ~20m on Hetzner agents
        requiresNotEc2Agent()
        requiresNotSharedHost()
    }

    features {
        publishBuildStatusToGithub(model)
    }

    dependencies {
        // If SanityCheck fails, Gradleception will definitely fail because the last build step is also sanityCheck
        dependsOn(RelativeId(SanityCheck.buildTypeId(model)))
    }

    /*
     To avoid unnecessary rerun, what we do here is a bit complicated:

     1. Build a Gradle distribution with a fixed timestamp and hash it, but never use this distribution.
     2. Build a Gradle distribution with this hash as a version + fixed timestamp -> dogfood-first
     3. Build a Gradle distribution using dogfood-first with this hash as a version + fixed timestamp different from the one above -> dogfood-second
     4. Use dogfood-second to run a Gradle build which runs tests.

     */
    val dogfoodTimestamp1 = "19800101010101+0000"
    val dogfoodTimestamp2 = "19800202020202+0000"
    val buildScanTagForType = buildScanTag("Gradleception")
    val buildScanTagForGroovy4 = buildScanTag("Groovy4")
    val buildScanTags = if (bundleGroovy4) listOf(buildScanTagForType, buildScanTagForGroovy4) else listOf(buildScanTagForType)
    val bundleGroovy4SysProp = "-DbundleGroovy4=true"
    val maybeBundleGroovy4SysProp = if (bundleGroovy4) bundleGroovy4SysProp else ""
    val defaultParameters = (buildToolGradleParameters() + buildScanTags + maybeBundleGroovy4SysProp + "-Porg.gradle.java.installations.auto-download=false").joinToString(separator = " ")

    params {
        // Override the default commit id so the build steps produce reproducible distribution
        param("env.BUILD_COMMIT_ID", "HEAD")
    }

    applyDefaults(
        model,
        this,
        ":distributions-full:install",
        extraParameters = "-Pgradle_installPath=dogfood-first-for-hash -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$dogfoodTimestamp1 $buildScanTagForType $maybeBundleGroovy4SysProp",
        extraSteps = {
            script {
                name = "CALCULATE_MD5_VERSION_FOR_DOGFOODING_DISTRIBUTION"
                workingDir = "%teamcity.build.checkoutDir%/dogfood-first-for-hash"
                scriptContent = """
                    set -x
                    MD5=`find . -type f | sort | xargs md5sum | md5sum | awk '{ print $1 }'`
                    echo "##teamcity[setParameter name='env.ORG_GRADLE_PROJECT_versionQualifier' value='gradleception-${'$'}MD5']"
                """.trimIndent()
            }
            gradleWrapper {
                name = "BUILD_GRADLE_DISTRIBUTION"
                tasks = "clean :distributions-full:install"
                gradleParams = "-Pgradle_installPath=dogfood-first -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$dogfoodTimestamp1 $defaultParameters"
            }

            localGradle {
                name = "BUILD_WITH_BUILT_GRADLE"
                tasks = "clean :distributions-full:install"
                gradleHome = "%teamcity.build.checkoutDir%/dogfood-first"
                gradleParams = "-Pgradle_installPath=dogfood-second -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$dogfoodTimestamp2 $defaultParameters"
            }

            localGradle {
                name = "QUICKCHECK_WITH_GRADLE_BUILT_BY_GRADLE"
                tasks = "clean sanityCheck test " + if (bundleGroovy4) "--dry-run" else ""
                gradleHome = "%teamcity.build.checkoutDir%/dogfood-second"
                gradleParams = defaultParameters
            }
        }
    )
})

fun BuildSteps.localGradle(init: GradleBuildStep.() -> Unit): GradleBuildStep =
    customGradle(init) {
        param("ui.gradleRunner.gradle.wrapper.useWrapper", "false")
        buildFile = ""
        skipConditionally()
    }
