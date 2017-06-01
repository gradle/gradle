package Gradle_Check_TestCoverageEmbeddedJava8Linux

import Gradle_Check_TestCoverageEmbeddedJava8Linux.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "aceaa13a-f058-4bca-8af0-c50361c72b75"
    extId = "Gradle_Check_TestCoverageEmbeddedJava8Linux"
    parentId = "Gradle_Check_Stage2"
    name = "Test Coverage - Embedded Java8 Linux"

    buildType(Gradle_Check_TestCoverageEmbeddedJava8Linux_1)
})
