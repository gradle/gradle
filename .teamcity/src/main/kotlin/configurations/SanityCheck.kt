package configurations

import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model))
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
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_SanityCheck"
    }
}
