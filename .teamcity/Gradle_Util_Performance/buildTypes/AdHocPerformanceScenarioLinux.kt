package Gradle_Util_Performance.buildTypes

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.builtInRemoteBuildCacheNode
import common.checkCleanM2
import common.gradleWrapper
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay

object AdHocPerformanceScenarioLinux : BuildType({
    uuid = "a3183d81-e07d-475c-8ef6-04ed60bf4053"
    name = "AdHoc Performance Scenario - Linux"
    id("Gradle_Util_Performance_AdHocPerformanceScenarioLinux")

    applyPerformanceTestSettings(timeout = 420)
    artifactRules = """
        subprojects/*/build/test-results-*.zip => results
        subprojects/*/build/tmp/**/log.txt => failure-logs
    """.trimIndent()

    params {
        text("baselines", "defaults", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("templates", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("channel", "adhoc")
        param("checks", "all")
        text("runs", "10", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("warmups", "3", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("scenario", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("flamegraphs", "--flamegraphs true")
        param("env.FG_HOME_DIR", "/opt/FlameGraph")
        param("additional.gradle.parameters", "")

        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.PATH", "%env.PATH%:/opt/swift/latest/usr/bin")
        param("env.HP_HOME_DIR", "/opt/honest-profiler")
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            gradleParams = (
                performanceTestCommandLine("clean %templates% performance:performanceAdHocTest", "%baselines%",
                    """--scenarios "%scenario%" --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% %flamegraphs% %additional.gradle.parameters%""")
                    + buildToolGradleParameters()
                    + builtInRemoteBuildCacheNode.gradleParameters(Os.linux)
                ).joinToString(separator = " ")
        }
        checkCleanM2()
    }
})
