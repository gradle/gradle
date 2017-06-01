package Gradle_Check_Stage6

import Gradle_Check_Stage6.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "2ed2480a-2281-4b4a-bca9-26faf2ce89b5"
    extId = "Gradle_Check_Stage6"
    parentId = "Gradle_Check"
    name = "Stage 6 - Soak Tests, Performance Experiments"
    description = "Triggered daily on Release and Master"

    buildType(Gradle_Check_SoakTestsJava8Windows)
    buildType(Gradle_Check_PerformanceExperimentsCoordinatorLinux)
    buildType(Gradle_Check_SoakTestsJava8Linux)
    buildType(Gradle_Check_Stage6_Passes)
    buildTypesOrder = arrayListOf(Gradle_Check_Stage6.buildTypes.Gradle_Check_Stage6_Passes, Gradle_Check_Stage6.buildTypes.Gradle_Check_SoakTestsJava8Linux, Gradle_Check_Stage6.buildTypes.Gradle_Check_SoakTestsJava8Windows, Gradle_Check_Stage6.buildTypes.Gradle_Check_PerformanceExperimentsCoordinatorLinux)
})
