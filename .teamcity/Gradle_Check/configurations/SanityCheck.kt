package configurations

import common.Os.LINUX
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    val javaHome = LINUX.buildJavaHome()
    params {
        param("env.JAVA_HOME", javaHome)
    }

    features {
        publishBuildStatusToGithub(model)
        triggeredOnPullRequests()
    }

    applyDefaults(
            model,
            this,
            "sanityCheck",
            extraParameters = "-DenableCodeQuality=true ${buildScanTag("SanityCheck")} " + explicitToolchains(javaHome).joinToString(" ")
    )
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectPrefix}SanityCheck"
    }
}
