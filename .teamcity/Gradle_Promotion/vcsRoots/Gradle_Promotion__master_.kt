package Gradle_Promotion.vcsRoots

import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

object Gradle_Promotion__master_ : GitVcsRoot({
    uuid = "0974a0e7-3f2f-4c3f-a185-aded6ac045ff"
    name = "Gradle Promotion"
    url = "https://github.com/gradle/gradle-promote.git"
    branch = "master"
    agentGitPath = "%env.TEAMCITY_GIT_PATH%"
    useMirrors = false
    authMethod = password {
        userName = "gradlewaregitbot"
        password = "credentialsJSON:69f980df-e4d1-4622-9f0f-5be59df88a04"
    }
})
