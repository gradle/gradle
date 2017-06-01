package Gradle_Check_Stage1

import Gradle_Check_Stage1.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "5d298c31-2341-4e48-82f1-96e0e6f4aaa9"
    extId = "Gradle_Check_Stage1"
    parentId = "Gradle_Check"
    name = "Stage 1 - Sanity Check and Distribution"

    buildType(Gradle_Check_Stage1_Passes)
    buildType(Gradle_Check_Stage1_BuildDistributions)
    buildType(Gradle_Check_Stage1_SanityCheck)
    buildTypesOrder = arrayListOf(Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_Passes, Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_SanityCheck, Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_BuildDistributions)
})
