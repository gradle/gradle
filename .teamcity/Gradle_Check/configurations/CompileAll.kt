package configurations

import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class CompileAll(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Compile All"
    description = "Compiles all the source code and warms up the build cache"

    params {
        param("env.JAVA_HOME", buildJavaHome)
    }

    if (model.publishStatusToGitHub) {
        features {
            publishBuildStatusToGithub()
        }
    }

    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true",
        extraParameters = buildScanTag("CompileAll")
    )

    artifactRules = """$artifactRules
        build/build-receipt.properties
    """.trimIndent()
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectPrefix}CompileAll"
    }
}
