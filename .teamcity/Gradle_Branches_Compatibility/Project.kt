package Gradle_Branches_Compatibility

import Gradle_Branches_Compatibility.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "6a760f70-09fc-4d3a-85f2-762c359c97e0"
    extId = "Gradle_Branches_Compatibility"
    parentId = "Gradle_Branches"
    name = "Compatibility"
    description = "runs compatibility checks to ensure gradle is working with other systems"

    buildType(Gradle_Branches_Compatibility_ApiChangeReport)
    buildType(Gradle_Branches_Compatibility_ColonyCompatibility)
})
