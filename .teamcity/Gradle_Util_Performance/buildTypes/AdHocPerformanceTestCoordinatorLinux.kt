package Gradle_Util_Performance.buildTypes

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.builtInRemoteBuildCacheNode
import common.checkCleanM2
import common.compileAllDependency
import common.distributedPerformanceTestParameters
import common.gradleWrapper
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType

object AdHocPerformanceTestCoordinatorLinux : BuildType({
    uuid = "a28ced77-77d1-41fd-bc3b-fe9c9016bf7b"
    id("Gradle_Util_Performance_PerformanceTestCoordinatorLinux")
    name = "AdHoc Performance Test Coordinator - Linux"

    applyPerformanceTestSettings(os = Os.linux, timeout = 420)

    maxRunningBuilds = 2

    params {
        param("performance.baselines", "defaults")
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                buildToolGradleParameters(isContinue = false)
                    + performanceTestCommandLine(task = "clean distributedPerformanceTests", baselines = "%performance.baselines%")
                    + distributedPerformanceTestParameters("Gradle_Check_IndividualPerformanceScenarioWorkersLinux")
                    + builtInRemoteBuildCacheNode.gradleParameters(Os.linux)
                ).joinToString(separator = " ")
        }
        checkCleanM2(Os.linux)
    }

    dependencies {
        compileAllDependency()
    }
})
