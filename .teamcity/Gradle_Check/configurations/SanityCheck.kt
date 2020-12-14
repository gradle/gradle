package configurations

import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    features {
        publishBuildStatusToGithub(model)
        triggeredOnPullRequests()
    }

    applyDefaults(
            model,
            this,
            "sanityCheck",
            extraParameters = "-DenableCodeQuality=true ${buildScanTag("SanityCheck")} " + "-Porg.gradle.java.installations.auto-download=false"
    )
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectPrefix}SanityCheck"
    }
}
