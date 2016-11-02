package Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17

import Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "0febafc6-01cd-47a3-8078-1b6007c8d310"
    extId = "Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17"
    parentId = "Gradle_Branches_CommitPhase_WindowsCommit"
    name = "Windows commit - Java 1.7"
    description = "Fast verification on Windows through in-process tests"

    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_2WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_7WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_1WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_8WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_3WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_6WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_4WindowsCommitJava)
    buildType(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_5WindowsCommitJava)
})
