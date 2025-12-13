package configurations

import common.FlakyTestStrategy
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
    flakyTestStrategy: FlakyTestStrategy,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
        val suffix = if (flakyTestStrategy == FlakyTestStrategy.ONLY)"_FlakyTestQuarantine" else ""
        id("${model.projectId}_SmokeTest_$id$suffix")
        name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.toCapitalized()} Linux$suffix"
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
                    "-PflakyTests=$flakyTestStrategy",
                ).joinToString(" "),
        )
    })
