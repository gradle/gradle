package Gradle_Check_Stage4

import Gradle_Check_Stage4.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "965a2e53-9e1a-4a8f-8ca7-a846127710d7"
    extId = "Gradle_Check_Stage4"
    parentId = "Gradle_Check"
    name = "Stage 4 - Test Forked Linux/Windows, Performance, Gradleception"

    buildType(Gradle_Check_Stage4_Passes)
    buildType(Gradle_Check_PerformanceTestCoordinatorLinux)
    buildType(Gradle_Check_GradleceptionJava8Linux)
    buildTypesOrder = arrayListOf(Gradle_Check_Stage4.buildTypes.Gradle_Check_Stage4_Passes, Gradle_Check_Stage4.buildTypes.Gradle_Check_GradleceptionJava8Linux, Gradle_Check_Stage4.buildTypes.Gradle_Check_PerformanceTestCoordinatorLinux)
})
