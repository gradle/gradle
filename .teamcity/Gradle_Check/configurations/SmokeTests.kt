package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel

object SmokeTests : BuildType({
    uuid = "${CIBuildModel.projectPrefix}SmokeTests"
    extId = uuid
    name = "Smoke Tests with 3rd Party Plugins - Java8 Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    params {
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    applyDefaults(this, "smokeTest:smokeTest", requiresDistribution = true)
})
