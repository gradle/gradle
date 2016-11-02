package Gradle_Branches_CommitPhase_LinuxCommit

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "0caab70d-eabe-4e49-997b-b9f6384c1a70"
    extId = "Gradle_Branches_CommitPhase_LinuxCommit"
    parentId = "Gradle_Branches_CommitPhase"
    name = "Linux commit"
    description = "Linux commit builds"
})
