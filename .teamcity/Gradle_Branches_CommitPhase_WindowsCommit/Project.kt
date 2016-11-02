package Gradle_Branches_CommitPhase_WindowsCommit

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "ece468e9-dc49-4112-9e4b-5556e1b8487d"
    extId = "Gradle_Branches_CommitPhase_WindowsCommit"
    parentId = "Gradle_Branches_CommitPhase"
    name = "Windows commit"
    description = "Windows commit builds"
})
