package Gradle_Branches_CoveragePhase

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "da3ccc6a-43d5-458e-aba3-c3284aa0c2eb"
    extId = "Gradle_Branches_CoveragePhase"
    parentId = "Gradle_Branches"
    name = "Coverage Phase"
    description = "Configurations that provide full test coverage on various platforms"
})
