package Gradle_Util_Performance

import Gradle_Util_Performance.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.Project

object Project : Project({
    uuid = "fdc4f15a-e253-4744-a1b3-bcac37b18189"
    id("Gradle_Util_Performance")
    parentId("Gradle_Util")
    name = "Performance"

    buildType(Gradle_Util_Performance_AdHocPerformanceScenarioLinux)
    buildType(Gradle_Util_Performance_PerformanceTestCoordinatorLinux)
})
