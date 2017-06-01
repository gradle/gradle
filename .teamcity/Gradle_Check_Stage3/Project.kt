package Gradle_Check_Stage3

import Gradle_Check_Stage3.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "478a3fbc-97e1-4992-8b95-4ab21969f688"
    extId = "Gradle_Check_Stage3"
    parentId = "Gradle_Check"
    name = "Stage 3 - Test Embedded Java7 Windows"

    buildType(Gradle_Check_Stage3_Passes)
})
