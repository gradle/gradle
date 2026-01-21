package common

import jetbrains.buildServer.configs.kotlin.DslContext

fun isSecurityFork(): Boolean =
    DslContext.settingsRoot.id
        .toString()
        .lowercase()
        .contains("security")

// GradleMaster -> Master
// GradleSecurityAdvisory84mwRelease -> SecurityAdvisory84mwRelease
val DslContext.uuidPrefix: String
    get() = settingsRoot.id.toString().substringAfter("Gradle")

data class VersionedSettingsBranch(
    val branchName: String,
) {
    companion object {
        private const val MASTER_BRANCH = "master"

        private const val RELEASE_BRANCH = "release"

        private const val EXPERIMENTAL_BRANCH = "xperimental"

        // master branch of gradle/gradle-promote
        private const val GRADLE_PROMOTE_MASTER_VCS_ROOT_ID = "Gradle_GradlePromoteMaster"

        // experimental branch of gradle/gradle-promote
        private const val GRADLE_PROMOTE_EXPERIMENTAL_VCS_ROOT_ID = "Gradle_GradlePromoteExperimental"

        private val OLD_RELEASE_PATTERN = "release(\\d+)x".toRegex()

        fun fromDslContext(): VersionedSettingsBranch = VersionedSettingsBranch(DslContext.getParameter("branch"))
    }

    val isMainBranch: Boolean
        get() = isMaster || isRelease
    val isMaster: Boolean
        get() = branchName == MASTER_BRANCH
    val isRelease: Boolean
        get() = branchName == RELEASE_BRANCH
    val isLegacyRelease: Boolean
        get() = OLD_RELEASE_PATTERN.matchEntire(branchName) != null
    val isExperimental: Boolean
        get() = branchName == EXPERIMENTAL_BRANCH

    fun vcsRootId() = DslContext.settingsRoot.id.toString()

    fun gradlePromoteVcsRootId() = if (isExperimental) GRADLE_PROMOTE_EXPERIMENTAL_VCS_ROOT_ID else GRADLE_PROMOTE_MASTER_VCS_ROOT_ID

    fun promoteNightlyTaskName() = nightlyTaskName("promote")

    fun prepNightlyTaskName() = nightlyTaskName("prep")

    fun promoteMilestoneTaskName(): String =
        when {
            isRelease -> "promoteReleaseMilestone"
            else -> "promoteMilestone"
        }

    fun promoteFinalReleaseTaskName(): String =
        when {
            isMaster -> throw UnsupportedOperationException("No final release job on master branch")
            isRelease -> "promoteFinalRelease"
            else -> "promoteFinalBackportRelease"
        }

    private fun nightlyTaskName(prefix: String): String =
        when {
            isMaster -> "${prefix}Nightly"
            isRelease -> "${prefix}ReleaseNightly"
            else -> "${prefix}PatchReleaseNightly"
        }
}
