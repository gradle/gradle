package configurations

import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    params {
        param("system.java9Home", "%linux.java9.oracle.64bit%")
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    if (model.publishStatusToGitHub) {
        features {
            publishBuildStatusToGithub()
        }
    }

    applyDefaults(
            model,
            this,
            "compileAll sanityCheck",
            extraParameters = "-DenableCodeQuality=true ${buildScanTag("SanityCheck")}"
    )

    artifactRules = """$artifactRules
        build/build-receipt.properties
    """.trimIndent()
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectPrefix}SanityCheck"
    }
}
