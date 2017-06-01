package Gradle_Check_TestCoverageParallelJava7IBMLinux

import Gradle_Check_TestCoverageParallelJava7IBMLinux.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "eacb67c6-da32-483b-89c9-b90047ddbbeb"
    extId = "Gradle_Check_TestCoverageParallelJava7IBMLinux"
    parentId = "Gradle_Check_Stage5"
    name = "Test Coverage - Parallel Java7IBM Linux"

    buildType(Gradle_Check_TestCoverageParallelJava7IBMLinux_1)
})
