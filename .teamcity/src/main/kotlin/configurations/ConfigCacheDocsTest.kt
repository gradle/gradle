package configurations

import common.JvmCategory
import common.toCapitalized
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.parallelTests
import model.CIBuildModel
import model.Stage

class ConfigCacheDocsTest(model: CIBuildModel, stage: Stage, testJava: JvmCategory) : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_ConfigCacheDocsTest_${testJava.version.name.toCapitalized()}")
    name = "Docs Test With Config Cache Enabled - ${testJava.version.name.toCapitalized()} Linux"
    description = "Docs test with config cache enabled"

    features {
        publishBuildStatusToGithub(model)
        parallelTests {
            numberOfBatches = 4
        }
    }

    applyTestDefaults(
        model,
        this,
        "docs:docsTest",
        timeout = 60,
        extraParameters = buildScanTag("ConfigCacheDocsTest") +
            " -PenableConfigurationCacheForDocsTests=true" +
            " -PtestJavaVersion=${testJava.version.major}" +
            " -PtestJavaVendor=${testJava.vendor.name}"
    )
})
