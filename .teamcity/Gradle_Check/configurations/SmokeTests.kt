package configurations

import common.JvmCategory
import common.Os.LINUX
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class SmokeTests(model: CIBuildModel, stage: Stage, testJava: JvmCategory, task: String = "smokeTest") : BaseGradleBuildType(model, stage = stage, init = {
    uuid = "${model.projectPrefix}${task.capitalize()}s${testJava.version.name.capitalize()}"
    id = AbsoluteId(uuid)
    name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.name.capitalize()} Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    val buildJavaHome = LINUX.buildJavaHome()
    val testJavaHome = LINUX.javaHome(testJava.version, testJava.vendor)
    params {
        param("env.ANDROID_HOME", LINUX.androidHome)
        param("env.JAVA_HOME", buildJavaHome)
    }

    features {
        publishBuildStatusToGithub(model)
    }

    applyTestDefaults(
        model,
        this,
        ":smoke-test:$task",
        timeout = 120,
        notQuick = true,
        extraParameters = buildScanTag("SmokeTests") + " -PtestJavaHome=$testJavaHome" + explicitToolchains("$buildJavaHome,$testJavaHome")
    )
})
