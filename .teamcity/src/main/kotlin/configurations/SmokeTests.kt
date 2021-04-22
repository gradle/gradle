package configurations

import common.JvmCategory
import model.CIBuildModel
import model.Stage

class SmokeTests(model: CIBuildModel, stage: Stage, testJava: JvmCategory, task: String = "smokeTest") : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_${task.capitalize()}s${testJava.version.name.capitalize()}")
    name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.name.capitalize()} Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    features {
        publishBuildStatusToGithub(model)
    }

    applyTestDefaults(
        model,
        this,
        ":smoke-test:$task",
        timeout = 120,
        notQuick = true,
        extraParameters = buildScanTag("SmokeTests") +
            " -PtestJavaVersion=${testJava.version.major}" +
            " -PtestJavaVendor=${testJava.vendor.name}" +
            " -Porg.gradle.java.installations.auto-download=false"
    )
})
