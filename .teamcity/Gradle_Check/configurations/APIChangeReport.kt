package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import model.CIBuildModel

object APIChangeReport : BuildType({
    uuid = "${CIBuildModel.projectPrefix}APIChangeReport"
    extId = uuid
    name = "API Change Report"
    description = "Generates a JDiff API report for the upcoming release"

    applyDefaultSettings(this, vcsRoot = "Gradle_Promotion__master_")

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean jdiff"
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
