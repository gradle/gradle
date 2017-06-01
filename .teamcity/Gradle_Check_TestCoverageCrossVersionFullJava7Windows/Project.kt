package Gradle_Check_TestCoverageCrossVersionFullJava7Windows

import Gradle_Check_TestCoverageCrossVersionFullJava7Windows.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "fbc9856c-0a19-4ffc-a2ed-933518cbff2d"
    extId = "Gradle_Check_TestCoverageCrossVersionFullJava7Windows"
    parentId = "Gradle_Check_Stage7"
    name = "Test Coverage - Cross-version Full Java7 Windows"

    buildType(Gradle_Check_TestCoverageCrossVersionFullJava7Windows_1)
})
