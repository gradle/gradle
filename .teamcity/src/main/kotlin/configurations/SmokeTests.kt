package configurations

import common.JvmCategory
import common.Os
import common.buildScanTagParam
import common.getBuildScanCustomValueParam
import common.requiresNotEc2Agent
import common.toCapitalized
import model.CIBuildModel
import model.Stage

class SmokeTests(
    model: CIBuildModel,
    stage: Stage,
    testJava: JvmCategory,
    id: String,
    task: String = "smokeTest",
    splitNumber: Int = 1,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
        id("${model.projectId}_SmokeTest_$id")
        name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.toCapitalized()} Linux"
        description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

        tcParallelTests(splitNumber)

        requirements {
            // Smoke tests is usually heavy and the build time is twice on EC2 agents
            requiresNotEc2Agent()
        }

        applyTestDefaults(
            model,
            this,
            ":smoke-test:$task",
            timeout = 120,
            extraParameters =
                listOf(
                    stage.getBuildScanCustomValueParam(),
                    buildScanTagParam("SmokeTests"),
                    "-PtestJavaVersion=${testJava.version.major}",
                    "-PtestJavaVendor=${testJava.vendor.name.lowercase()}",
                ).joinToString(" "),
        )
    })
