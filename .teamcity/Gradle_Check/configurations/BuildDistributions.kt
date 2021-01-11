package configurations

import common.Os.LINUX
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class BuildDistributions(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = "${model.projectPrefix}BuildDistributions"
    id = AbsoluteId(uuid)
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
        subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()

    params {
        param("env.JAVA_HOME", LINUX.javaHomeForGradle())
    }
})
