package Gradle_Util_Performance.buildTypes

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.builtInRemoteBuildCacheNode
import common.checkCleanM2
import common.gradleWrapper
import common.individualPerformanceTestArtifactRules
import common.killGradleProcessesStep
import common.performanceTestCommandLine
import common.removeSubstDirOnWindows
import common.substDirOnWindows
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay

abstract class AdHocPerformanceScenario(os: Os) : BuildType({
    val id = "Gradle_Util_Performance_AdHocPerformanceScenario${os.asName()}"
    this.uuid = id
    name = "AdHoc Performance Scenario - ${os.asName()}"
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

        when (os) {
            Os.WINDOWS -> {
                param("flamegraphs", "--flamegraphs false")
            }
            else -> {
                param("flamegraphs", "--flamegraphs true")
                param("env.FG_HOME_DIR", "/opt/FlameGraph")
                param("env.PATH", "%env.PATH%:/opt/swift/4.2.3/usr/bin")
                param("env.HP_HOME_DIR", "/opt/honest-profiler")
            }
        }

        param("additional.gradle.parameters", "")
        param("env.ANDROID_HOME", os.androidHome)
    }

    steps {
        killGradleProcessesStep(os)
        substDirOnWindows(os)
        gradleWrapper {
            name = "GRADLE_RUNNER"
            workingDir = os.perfTestWorkingDir
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
        removeSubstDirOnWindows(os)
        checkCleanM2(os)
    }
})

object AdHocPerformanceScenarioLinux : AdHocPerformanceScenario(Os.LINUX)
object AdHocPerformanceScenarioWindows : AdHocPerformanceScenario(Os.WINDOWS)
object AdHocPerformanceScenarioMacOS : AdHocPerformanceScenario(Os.MACOS)
