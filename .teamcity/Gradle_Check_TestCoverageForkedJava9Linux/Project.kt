package Gradle_Check_TestCoverageForkedJava9Linux

import Gradle_Check_TestCoverageForkedJava9Linux.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "2192eba6-44c5-415e-8121-ccfd029a6693"
    extId = "Gradle_Check_TestCoverageForkedJava9Linux"
    parentId = "Gradle_Check_Stage5"
    name = "Test Coverage - Forked Java9 Linux"

    buildType(Gradle_Check_TestCoverageForkedJava9Linux_1)
})
