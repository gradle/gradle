package configurations

import common.BuildToolBuildJvm
import common.Jvm
import common.Os
import common.buildScanTagParam
import common.buildToolGradleParameters
import common.customGradle
import common.dependsOn
import common.functionalTestParameters
import common.getBuildScanCustomValueParam
import common.gradleWrapper
import common.requiresNotEc2Agent
import common.requiresNotSharedHost
import common.skipConditionally
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

/**
 * Build a Gradle distribution (dogfood-first) and use this distribution to build a distribution again (dogfood-second).
 * Use `dogfood-second` to run `test sanityCheck`.
 */
class Gradleception(
    model: CIBuildModel,
    stage: Stage,
    buildJvm: Jvm,
    jvmDescription: String,
    bundleGroovy4: Boolean = false,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
    val idParts = mutableListOf<String>()
    val labels = mutableListOf<String>()
    val descriptionParts = mutableListOf<String>()
    if (bundleGroovy4) {
        labels += "Groovy 4.x"
        idParts += "Groovy4"
        descriptionParts += "bundling Groovy 4"
    }
    val vendor = buildJvm.vendor.displayName
    val version = buildJvm.version.major
    if (buildJvm != BuildToolBuildJvm) {
        idParts += "Java$jvmDescription"
        descriptionParts += "with Java$version $vendor"
    }
    labels += "Java$version $vendor"
    labels += "Linux"
    val idSuffix = if (idParts.isNotEmpty()) {
        "With${idParts.joinToString(separator = "And")}"
    } else ""
    id("${model.projectId}_Gradleception$idSuffix")
    name = "Gradleception - ${labels.joinToString(separator = " ")}"
    val descriptionSuffix = if (descriptionParts.isNotEmpty()) {
        " (${descriptionParts.joinToString(separator = ", ")})"
    } else ""
    description =
        "Builds Gradle with the version of Gradle which is currently under development (twice)$descriptionSuffix"

    requirements {
        // Gradleception is a heavy build which runs ~40m on EC2 agents but only ~20m on Hetzner agents
        requiresNotEc2Agent()
        requiresNotSharedHost()
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
    val buildScanTagForType = buildScanTagParam("Gradleception")
    val buildScanTagForGroovy4 = buildScanTagParam("Groovy4")
    val buildScanTags =
        if (bundleGroovy4) listOf(buildScanTagForType, buildScanTagForGroovy4) else listOf(buildScanTagForType)
    val extraSysProp = mutableListOf<String>()
    if (bundleGroovy4) {
        extraSysProp += "-DbundleGroovy4=true"
    }
    if (buildJvm.version != BuildToolBuildJvm.version) {
        extraSysProp += "-Dorg.gradle.ignoreBuildJavaVersionCheck=true"
    }
    val defaultParameters =
        (buildToolGradleParameters() + buildScanTags + extraSysProp + functionalTestParameters(Os.LINUX)).joinToString(
            separator = " "
        )

    params {
        // Override the default commit id so the build steps produce reproducible distribution
        param("env.BUILD_COMMIT_ID", "HEAD")
    }

    if (buildJvm.version != BuildToolBuildJvm.version) {
        steps.gradleWrapper {
            name = "UPDATE_DAEMON_JVM_CRITERIA_FILE"
            tasks = "updateDaemonJvm --jvm-version=${buildJvm.version.major}"
            gradleParams = defaultParameters
        }
    }

    applyDefaults(
        model,
        this,
        ":distributions-full:install",
        extraParameters =
        (
            listOf(
                stage.getBuildScanCustomValueParam(),
                "-Pgradle_installPath=dogfood-first-for-hash",
                "-PignoreIncomingBuildReceipt=true",
                "-PbuildTimestamp=$dogfoodTimestamp1",
                buildScanTagForType,
            ) + extraSysProp
            ).joinToString(" "),
        buildJvm = buildJvm,
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
                gradleParams =
                    "-Pgradle_installPath=dogfood-first -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$dogfoodTimestamp1 $defaultParameters"
            }

            localGradle {
                name = "BUILD_WITH_BUILT_GRADLE"
                tasks = "clean :distributions-full:install"
                gradleHome = "%teamcity.build.checkoutDir%/dogfood-first"
                gradleParams =
                    "-Pgradle_installPath=dogfood-second -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$dogfoodTimestamp2 $defaultParameters"
            }

            localGradle {
                name = "QUICKCHECK_WITH_GRADLE_BUILT_BY_GRADLE"
                tasks = "clean sanityCheck test -PflakyTests=exclude"
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
