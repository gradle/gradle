package vcsroots

import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.VcsSettings

val gradlePromotionMaster = "Gradle_GradlePromoteMaster"
val gradlePromotionBranches = "Gradle_GradlePromoteBranches"

fun VcsSettings.useAbsoluteVcs(absoluteId: String) {
    root(AbsoluteId(absoluteId))

    checkoutMode = CheckoutMode.ON_AGENT
    this.cleanCheckout = cleanCheckout
    showDependenciesChanges = true
}
