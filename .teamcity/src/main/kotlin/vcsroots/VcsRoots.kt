package vcsroots

import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.VcsSettings

val gradlePromotionMaster = "Gradle_GradlePromoteMaster"
val gradlePromotionBranches = "Gradle_GradlePromoteBranches"

fun VcsSettings.useAbsoluteVcs(absoluteId: String) {
    root(AbsoluteId(absoluteId))

    checkoutMode = CheckoutMode.ON_AGENT
    this.cleanCheckout = cleanCheckout
    showDependenciesChanges = true
}
