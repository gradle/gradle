package Gradle_Check_Stage7

import Gradle_Check_Stage7.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "2b1ca8e4-b319-4210-9c6e-36000c5783c9"
    extId = "Gradle_Check_Stage7"
    parentId = "Gradle_Check"
    name = "Stage 7 - Test Cross-version (All Versions), No-daemon, Performance Historical"
    description = "Triggered weekly on Release and Master"

    buildType(Gradle_Check_PerformanceHistoricalBuild)
    buildType(Gradle_Check_Stage7_Passes)
    buildTypesOrder = arrayListOf(Gradle_Check_Stage7.buildTypes.Gradle_Check_Stage7_Passes, Gradle_Check_Stage7.buildTypes.Gradle_Check_PerformanceHistoricalBuild)
})
