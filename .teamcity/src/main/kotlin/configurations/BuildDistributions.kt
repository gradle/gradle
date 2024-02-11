package configurations

import common.Os.LINUX
import model.CIBuildModel
import model.Stage

class BuildDistributions(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_BuildDistributions")
    name = "Build Distributions"
    description = "Creation and verification of the distribution and documentation"

    applyDefaults(
        model,
        this,
        "packageBuild",
        extraParameters = buildScanTag("BuildDistributions") +
            " -PtestJavaVersion=${LINUX.buildJavaVersion.major}" +
            " -Porg.gradle.java.installations.auto-download=false"
    )

    features {
        publishBuildStatusToGithub(model)
    }

    artifactRules = """$artifactRules
        subprojects/distributions-full/build/distributions/*.zip => distributions
        platforms/core-runtime/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
})
