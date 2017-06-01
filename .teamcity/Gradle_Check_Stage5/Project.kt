package Gradle_Check_Stage5

import Gradle_Check_Stage5.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "cbcb11d3-1666-45a7-a425-6506e1d49158"
    extId = "Gradle_Check_Stage5"
    parentId = "Gradle_Check"
    name = "Stage 5 - Test Parallel, Java9, IBM VM, Cross-Version, Smoke Tests, Colony"
    description = "Triggered for every commit on Release and Master"

    buildType(Gradle_Check_Stage5_Passes)
    buildType(Gradle_Check_ColonyCompatibility)
    buildType(Gradle_Check_SmokeTestsJava8Linux)
    buildTypesOrder = arrayListOf(Gradle_Check_Stage5.buildTypes.Gradle_Check_Stage5_Passes, Gradle_Check_Stage5.buildTypes.Gradle_Check_SmokeTestsJava8Linux, Gradle_Check_Stage5.buildTypes.Gradle_Check_ColonyCompatibility)
})
