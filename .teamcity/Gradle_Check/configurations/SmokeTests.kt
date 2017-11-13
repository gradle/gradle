package configurations

import model.CIBuildModel

class SmokeTests(model: CIBuildModel) : BaseGradleBuildType(model, {
    uuid = "${model.projectPrefix}SmokeTests"
    extId = uuid
    name = "Smoke Tests with 3rd Party Plugins - Java8 Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    params {
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    applyDefaults(model, this, "smokeTest:smokeTest", notQuick = true)
})
