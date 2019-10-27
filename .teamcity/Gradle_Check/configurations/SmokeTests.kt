package configurations

import common.JvmCategory
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class SmokeTests(model: CIBuildModel, stage: Stage, testJava: JvmCategory) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = "${model.projectPrefix}SmokeTests${testJava.version.name.capitalize()}"
    id = AbsoluteId(uuid)
    name = "Smoke Tests with 3rd Party Plugins - ${testJava.version.name.capitalize()} Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    params {
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", buildJavaHome())
        param("env.GRADLE_BUILD_JAVA_HOME", buildJavaHome())
    }

    features {
        publishBuildStatusToGithub(model)
    }

    applyTestDefaults(
        model,
        this,
        "smokeTest:smokeTest",
        notQuick = true,
        extraParameters = buildScanTag("SmokeTests") + " -PtestJavaHome=%linux.${testJava.version.name}.${testJava.vendor.name}.64bit%"
    )
})
