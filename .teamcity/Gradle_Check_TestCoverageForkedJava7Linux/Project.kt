package Gradle_Check_TestCoverageForkedJava7Linux

import Gradle_Check_TestCoverageForkedJava7Linux.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "1820eb2f-91d2-4294-b704-76bc8e112b46"
    extId = "Gradle_Check_TestCoverageForkedJava7Linux"
    parentId = "Gradle_Check_Stage4"
    name = "Test Coverage - Forked Java7 Linux"


    for (bucket in 1..8) {
        buildType(Gradle_Check_TestCoverageForkedJava7Linux_1("" + bucket))
    }
})
