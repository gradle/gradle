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
        name = "Build-logic test"
        description = "Run :build-logic:test"

        features {
            publishBuildStatusToGithub(model)
        }

        applyDefaults(
            model,
            this,
            ":build-logic:test -PskipBuildLogicTests=false",
            extraParameters =
                listOf(
                    stage.getBuildScanCustomValueParam(),
                    buildScanTagParam("BuildLogitTest"),
                    "-Porg.gradle.java.installations.auto-download=false",
                ).joinToString(" "),
        )
    }) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_BuildLogicTest"
    }
}
