package configurations

import common.JvmCategory
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class SmokeTests(model: CIBuildModel, stage: Stage, testJava: JvmCategory, task: String = "smokeTest") : BaseGradleBuildType(model, stage = stage, init = {
    uuid = "${model.projectPrefix}${task.capitalize()}s${testJava.version.name.capitalize()}"
    id = AbsoluteId(uuid)
    name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.name.capitalize()} Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    params {
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", buildJavaHome())
    }

    features {
        publishBuildStatusToGithub(model)
    }

    applyTestDefaults(
        model,
        this,
        ":smokeTest:$task",
        notQuick = true,
        extraParameters = buildScanTag("SmokeTests") + " -PtestJavaHome=%linux.${testJava.version.name}.${testJava.vendor.name}.64bit%"
    )
})
