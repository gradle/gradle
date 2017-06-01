package Gradle_Check_Stage2

import Gradle_Check_Stage2.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "c3269e7a-a10e-432e-8127-e67b6e1b8803"
    extId = "Gradle_Check_Stage2"
    parentId = "Gradle_Check"
    name = "Stage 2 - Test Embedded Java8 Linux"

    buildType(Gradle_Check_Stage2_Passes)
})
