package util

import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object UtilPerformanceProject : Project({
    id("Util_Performance")
    name = "Performance"

    buildType(AdHocPerformanceScenarioLinux)
    buildType(AdHocPerformanceScenarioWindows)
    buildType(AdHocPerformanceScenarioMacOS)
    buildType(AdHocPerformanceScenarioMacM1)

    params {
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
})
