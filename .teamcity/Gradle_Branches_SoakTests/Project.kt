package Gradle_Branches_SoakTests

import Gradle_Branches_SoakTests.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "a0207b01-9e3b-40fe-b435-405a10115518"
    extId = "Gradle_Branches_SoakTests"
    parentId = "Gradle_Branches"
    name = "Soak tests"
    description = "Long-running integration tests"

    buildType(Gradle_Branches_SoakTests_WindowsSoakTests)
    buildType(Gradle_Branches_SoakTests_LinuxSoakTests)
})
