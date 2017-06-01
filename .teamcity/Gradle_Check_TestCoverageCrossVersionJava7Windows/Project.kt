package Gradle_Check_TestCoverageCrossVersionJava7Windows

import Gradle_Check_TestCoverageCrossVersionJava7Windows.buildTypes.Gradle_Check_TestCoverageCrossVersionJava7Windows_1
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "0659aea8-1a50-4221-8709-e3e03793f42c"
    extId = "Gradle_Check_TestCoverageCrossVersionJava7Windows"
    parentId = "Gradle_Check_Stage5"
    name = "Test Coverage - Cross-version Java7 Windows"

    for (bucket in 1..8) {
        buildType(Gradle_Check_TestCoverageCrossVersionJava7Windows_1("" + bucket))
    }
})
