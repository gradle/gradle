package configurations_g4

import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import model_g4.CIBuildModel
import model_g4.Stage

class CompileAll(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Compile All"
    description = "Compiles all the source code and warms up the build cache"

    params {
        param("system.java9Home", "%linux.java9.oracle.64bit%")
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
        ":createBuildReceipt -PignoreIncomingBuildReceipt=true compileAll",
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
