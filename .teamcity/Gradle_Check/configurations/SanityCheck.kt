package configurations

import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = uuid
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    params {
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
