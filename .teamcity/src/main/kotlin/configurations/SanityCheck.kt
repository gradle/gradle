package configurations

import common.Os
import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
    id(buildTypeId(model))
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    features {
        publishBuildStatusToGithub(model)
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
