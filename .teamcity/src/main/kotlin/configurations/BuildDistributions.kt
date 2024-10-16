package configurations

import common.Os.LINUX
import common.buildScanTagParam
import common.getBuildScanCustomValueParam
import model.CIBuildModel
import model.Stage

class BuildDistributions(model: CIBuildModel, stage: Stage) : OsAwareBaseGradleBuildType(os = LINUX, stage = stage, init = {
    id("${model.projectId}_BuildDistributions")
    name = "Build Distributions"
    description = "Creation and verification of the distribution and documentation"

    applyDefaults(
        model,
        this,
        "packageBuild",
        extraParameters =
        listOf(
            stage.getBuildScanCustomValueParam(),
            buildScanTagParam("BuildDistributions"),
            "-PtestJavaVersion=${LINUX.buildJavaVersion.major}",
            "-Porg.gradle.java.installations.auto-download=false"
        ).joinToString(" ")
    )

    features {
        publishBuildStatusToGithub(model)
    }

    artifactRules = """$artifactRules
        subprojects/distributions-full/build/distributions/*.zip => distributions
        platforms/core-runtime/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
})
