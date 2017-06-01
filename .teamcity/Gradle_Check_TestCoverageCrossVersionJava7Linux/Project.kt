package Gradle_Check_TestCoverageCrossVersionJava7Linux

import Gradle_Check_TestCoverageCrossVersionJava7Linux.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "98fdfa54-f9cc-49c8-8940-0184cafcc9a7"
    extId = "Gradle_Check_TestCoverageCrossVersionJava7Linux"
    parentId = "Gradle_Check_Stage5"
    name = "Test Coverage - Cross-version Java7 Linux"

    buildType(Gradle_Check_TestCoverageCrossVersionJava7Linux_1)
})
