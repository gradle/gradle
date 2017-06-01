package Gradle_Check_TestCoverageEmbeddedJava7Windows

import Gradle_Check_TestCoverageEmbeddedJava7Windows.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "05fc0c64-4e60-4203-9861-cc0d75138d98"
    extId = "Gradle_Check_TestCoverageEmbeddedJava7Windows"
    parentId = "Gradle_Check_Stage3"
    name = "Test Coverage - Embedded Java7 Windows"

    buildType(Gradle_Check_TestCoverageEmbeddedJava7Windows_1)
})
