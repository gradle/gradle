package configurations

import common.Os
import common.buildScanTagParam
import common.getBuildScanCustomValueParam
import model.CIBuildModel
import model.Stage

class BuildLogicTest(
    model: CIBuildModel,
    stage: Stage,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
        id(buildTypeId(model))
        name = "Build-logic checks"
        description = "Run check on all build-logic builds"

        features {
            publishBuildStatusToGithub(model)
        }

        applyDefaults(
            model,
            this,
            "checkBuildLogic",
            extraParameters =
                listOf(
                    stage.getBuildScanCustomValueParam(),
                    buildScanTagParam("BuildLogicTest"),
                    "-Dorg.gradle.java.installations.auto-download=false",
                    "-Porg.gradle.java.installations.auto-download=false",
                ).joinToString(" "),
        )
    }) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_BuildLogicTest"
    }
}
