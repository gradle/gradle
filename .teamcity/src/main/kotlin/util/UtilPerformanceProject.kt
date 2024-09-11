package util

import jetbrains.buildServer.configs.kotlin.Project

object UtilPerformanceProject : Project({
    id("Util_Performance")
    name = "Performance"

    buildType(AdHocPerformanceScenarioLinux)
    buildType(AdHocPerformanceScenarioWindows)
    buildType(AdHocPerformanceScenarioMacOS)
    buildType(AdHocPerformanceScenarioMacAppleSilicon)

    params {
        param("env.DEVELOCITY_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
})
