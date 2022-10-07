package configurations

import common.JvmCategory
import common.Os.LINUX
import common.cleanAndroidUserHome
import common.requiresNotEc2Agent
import model.CIBuildModel
import model.Stage

class SmokeTests(model: CIBuildModel, stage: Stage, testJava: JvmCategory, task: String = "smokeTest") : BaseGradleBuildType(model, stage = stage, init = {
    id("${model.projectId}_${task.capitalize()}s${testJava.version.name.capitalize()}")
    name = "Smoke Tests with 3rd Party Plugins ($task) - ${testJava.version.name.capitalize()} Linux"
    description = "Smoke tests against third party plugins to see if they still work with the current Gradle version"

    params {
        param("env.ANDROID_HOME", LINUX.androidHome)
        param("env.JAVA_HOME", LINUX.buildJavaHome())
    }

    features {
        publishBuildStatusToGithub(model)
    }
    requirements {
        // Smoke tests is usually heavy and the build time is twice on EC2 agents
        requiresNotEc2Agent()
    }

    applyTestDefaults(
        model,
        this,
        ":smoke-test:$task",
        timeout = 120,
        notQuick = true,
        extraParameters = buildScanTag("SmokeTests") + " -PtestJavaHome=%linux.${testJava.version.name}.${testJava.vendor.name}.64bit%",
        preSteps = {
            cleanAndroidUserHome()
        }
    )
})
