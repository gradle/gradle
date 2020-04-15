package Gradle_Util_Performance

import Gradle_Util_Performance.buildTypes.AdHocPerformanceScenarioLinux
import Gradle_Util_Performance.buildTypes.AdHocPerformanceTestCoordinatorLinux
import Gradle_Util_Performance.buildTypes.AdHocPerformanceTestCoordinatorWindows
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object Project : Project({
    uuid = "fdc4f15a-e253-4744-a1b3-bcac37b18189"
    id("Gradle_Util_Performance")
    parentId("Gradle_Util")
    name = "Performance"

    buildType(AdHocPerformanceScenarioLinux)
    buildType(AdHocPerformanceTestCoordinatorLinux)
    buildType(AdHocPerformanceTestCoordinatorWindows)
})
