package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import model.CIBuildModel

object ColonyCompatibility : BuildType({
    uuid = "${CIBuildModel.projectPrefix}ColonyCompatibility"
    extId = uuid
    name = "Colony Compatibility"
    description = "Check Gradle against latest development version of Colony"

    applyDefaultSettings(this, vcsRoot = "Colony_ColonyMaster")

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean :gradle-test:test"
            gradleParams = gradleParameters.joinToString(separator = " ")
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = m2CleanScriptLinux
        }
    }

    applyDefaultDependencies(this, true)
})
