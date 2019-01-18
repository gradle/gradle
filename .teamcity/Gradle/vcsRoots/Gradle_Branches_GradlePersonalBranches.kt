package Gradle.vcsRoots

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

object Gradle_Branches_GradlePersonalBranches : GitVcsRoot({
    uuid = "fa8766c3-9ef7-4e6a-b55b-b3876053d3e9"
    name = "Gradle"
    url = "git://github.com/gradle/gradle.git"
    branch = "dummy-default-branch-for-tc"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*/head)
    """.trimIndent()
    agentCleanPolicy = GitVcsRoot.AgentCleanPolicy.NEVER
    useMirrors = false
})
