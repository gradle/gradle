package configurations

import Gradle_Check.configurations.nonMasterReleaseCommunityBranchFilter
import Gradle_Check.configurations.triggerExcludes
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    params {
        param("env.JAVA_HOME", buildJavaHome())
    }

    features {
        publishBuildStatusToGithub(model)
    }

    triggers.vcs {
        quietPeriodMode = VcsTrigger.QuietPeriodMode.DO_NOT_USE
        triggerRules = triggerExcludes
        branchFilter = nonMasterReleaseCommunityBranchFilter
    }

    applyDefaults(
            model,
            this,
            "sanityCheck",
            extraParameters = "-DenableCodeQuality=true ${buildScanTag("SanityCheck")}"
    )
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectPrefix}SanityCheck"
    }
}
