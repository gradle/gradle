package Gradle_Promotion.vcsRoots

import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

object Gradle_Promotion_GradlePromotionBranches : GitVcsRoot({
    uuid = "e4bc6ac6-ab3f-4459-b4c4-7d6ba6e2cbf6"
    name = "Gradle Promotion Branches"
    url = "https://github.com/gradle/gradle-promote.git"
    branchSpec = "+:refs/heads/*"
    agentGitPath = "%env.TEAMCITY_GIT_PATH%"
    useMirrors = false
    authMethod = password {
        userName = "gradlewaregitbot"
        password = "credentialsJSON:5306bfc7-041e-46e8-8d61-1d49424e7b04"
    }
})
