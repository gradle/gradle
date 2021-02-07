package vcsroots

import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.VcsSettings
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

object GradleAllBranches : GitVcsRoot({
    name = "Gradle All Branches"
    url = "https://github.com/gradle/gradle.git"
    branch = "master"
    branchSpec = "+:refs/heads/*"
    agentGitPath = "%env.TEAMCITY_GIT_PATH%"
    useMirrors = false
})

val gradleMasterVersionedSettings = "GradleMaster"
val gradleReleaseVersionedSettings = "GradleRelease"
val gradlePromotionMaster = "Gradle_GradlePromoteMaster"
val gradlePromotionBranches = "Gradle_GradlePromoteBranches"

fun VcsSettings.useAbsoluteVcs(absoluteId: String) {
    root(AbsoluteId(absoluteId))

    checkoutMode = CheckoutMode.ON_AGENT
    this.cleanCheckout = cleanCheckout
    showDependenciesChanges = true
}
