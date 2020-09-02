package Gradle_Util_Performance.buildTypes

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.builtInRemoteBuildCacheNode
import common.checkCleanM2
import common.gradleWrapper
import common.individualPerformanceTestArtifactRules
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script

abstract class AdHocPerformanceScenario(os: Os) : BuildType({
    val id = "Gradle_Util_Performance_AdHocPerformanceScenario${os.name.toLowerCase().capitalize()}"
    this.uuid = id
    name = "AdHoc Performance Scenario - ${os.name.toLowerCase().capitalize()}"
    id(id)

    applyPerformanceTestSettings(os = os, timeout = 420)
    artifactRules = individualPerformanceTestArtifactRules

    params {
        text("baselines", "defaults", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("templates", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("channel", "adhoc")
        param("checks", "all")
        text("runs", "10", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("warmups", "3", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("scenario", "", display = ParameterDisplay.PROMPT, allowEmpty = false)

        if (os != Os.WINDOWS) {
            param("flamegraphs", "--flamegraphs true")
            param("env.FG_HOME_DIR", "/opt/FlameGraph")
            param("env.PATH", "%env.PATH%:/opt/swift/4.2.3/usr/bin")
            param("env.HP_HOME_DIR", "/opt/honest-profiler")
        } else {
            param("flamegraphs", "--flamegraphs false")
        }

        param("additional.gradle.parameters", "")
        param("env.ANDROID_HOME", os.androidHome)
    }

    steps {
        script {
            name = "KILL_GRADLE_PROCESSES"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = os.killAllGradleProcesses
        }
        gradleWrapper {
            name = "GRADLE_RUNNER"
            gradleParams = (
                performanceTestCommandLine(
                    "clean %templates% performance:performanceAdHocTest",
                    "%baselines%",
                    """--scenarios "%scenario%" --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% %flamegraphs% %additional.gradle.parameters%""",
                    os
                ) +
                    buildToolGradleParameters(isContinue = false) +
                    builtInRemoteBuildCacheNode.gradleParameters(os)
                ).joinToString(separator = " ")
        }
        checkCleanM2(os)
    }
})

object AdHocPerformanceScenarioLinux : AdHocPerformanceScenario(Os.LINUX)
object AdHocPerformanceScenarioWindows : AdHocPerformanceScenario(Os.WINDOWS)
object AdHocPerformanceScenarioMacOS : AdHocPerformanceScenario(Os.MACOS)
