package Gradle_Branches_CommitPhase

import Gradle_Branches_CommitPhase.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "7a6954ab-e657-43ce-b032-153757f35aa2"
    extId = "Gradle_Branches_CommitPhase"
    parentId = "Gradle_Branches"
    name = "Commit Phase"
    description = "Configurations for daily development"

    buildType(Gradle_Branches_CommitPhase_BuildDistributions)
    buildType(Gradle_Branches_CommitPhase_SanityCheck)
})
