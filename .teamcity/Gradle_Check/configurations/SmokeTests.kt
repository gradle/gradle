package configurations

import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class SmokeTests(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = "${model.projectPrefix}SmokeTests"
    id = AbsoluteId(uuid)
    name = "Smoke Tests with 3rd Party Plugins - Java8 Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    params {
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", buildJavaHome)
    }

    applyDefaults(
            model,
            this,
            "smokeTest:smokeTest",
            notQuick = true,
            extraParameters = buildScanTag("SmokeTests") + " -PtestJavaHome=${smokeTestJavaHome}"
    )
})
