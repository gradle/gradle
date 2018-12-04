package configurations_g4

import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import model_g4.CIBuildModel
import model_g4.Stage

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
