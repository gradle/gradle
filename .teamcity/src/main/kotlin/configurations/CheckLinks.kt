package configurations

import common.Os
import common.buildScanTagParam
import model.CIBuildModel
import model.Stage

class CheckLinks(
    model: CIBuildModel,
    stage: Stage,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, failStage = false, init = {
        id("${model.projectId}_CheckLinks")
        name = "CheckLinks"
        description = "Check links in documentations"

        applyDefaults(
            model,
            this,
            ":docs:checkLinks",
            extraParameters = buildScanTagParam("CheckLinks") + " " + "-Porg.gradle.java.installations.auto-download=false",
        )
    })
