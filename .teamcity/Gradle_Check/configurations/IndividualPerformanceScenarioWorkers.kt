package configurations

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.gradleWrapper
import common.individualPerformanceTestArtifactRules
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import model.CIBuildModel

class IndividualPerformanceScenarioWorkers(model: CIBuildModel, os: Os = Os.linux) : BaseGradleBuildType(model, init = {
    uuid = model.projectPrefix + "IndividualPerformanceScenarioWorkers${os.name.capitalize()}"
    id = AbsoluteId(uuid)
    name = "Individual Performance Scenario Workers - ${os.name.capitalize()}"

    applyPerformanceTestSettings(os = os, timeout = 420)
    artifactRules = individualPerformanceTestArtifactRules

    params {
        param("baselines", "defaults")
        param("templates", "")
        param("channel", "commits")
        param("checks", "all")
        param("runs", "defaults")
        param("warmups", "defaults")
        param("scenario", "")

        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.PATH", "%env.PATH%:/opt/swift/4.2.3/usr/bin")
    }

    steps {
        script {
            name = "KILL_GRADLE_PROCESSES"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = killAllGradleProcesses
        }
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                performanceTestCommandLine(
                    "clean %templates% fullPerformanceTests",
                    "%baselines%",
                    """--scenarios "%scenario%" --warmups %warmups% --runs %runs% --checks %checks% --channel %channel%""",
                    individualPerformanceTestJavaHome(os)
                , os) +
                    buildToolGradleParameters(isContinue = false, os = os) +
                    buildScanTag("IndividualPerformanceScenarioWorkers") +
                    model.parentBuildCache.gradleParameters(os)
                ).joinToString(separator = " ")
        }
        checkCleanM2(os)
    }

    applyDefaultDependencies(model, this)
})
