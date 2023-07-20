package common

import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext

fun isSecurityFork(): Boolean {
    return DslContext.settingsRoot.id.toString().lowercase().contains("security")
}

// GradleMaster -> Master
// GradleSecurityAdvisory84mwRelease -> SecurityAdvisory84mwRelease
val DslContext.uuidPrefix: String
    get() = settingsRoot.id.toString().substringAfter("Gradle")

data class VersionedSettingsBranch(val branchName: String) {
    /**
     * 0~23.
     * To avoid nightly promotion jobs running at the same time,
     * we run each branch on different hours.
     * master - 0:00
     * release - 1:00
     * release6x - 2:00
     * release7x - 3:00
     * ...
     * releaseNx - (N-4):00
     */
    val nightlyPromotionTriggerHour: Int? = determineNightlyPromotionTriggerHour(branchName)

    /**
     * Whether the <a href="https://www.jetbrains.com/help/teamcity/configuring-vcs-triggers.html">VCS trigger</a>
     * should be enabled, i.e. when new commits are pushed to this branch, should a ReadyForNightly job
     * be triggered automatically?
     *
     * Currently, we only enable VCS trigger for `master`/`release`/`releaseNx` branches.
     */
    val enableVcsTriggers: Boolean = nightlyPromotionTriggerHour != null

    companion object {
        private
        const val MASTER_BRANCH = "master"

        private
        const val RELEASE_BRANCH = "release"

        private
        const val EXPERIMENTAL_BRANCH = "experimental"

        private
        val OLD_RELEASE_PATTERN = "release(\\d+)x".toRegex()

        fun fromDslContext(): VersionedSettingsBranch {
            val branch = DslContext.getParameter("Branch")
            // TeamCity uses a dummy name when first running the DSL
            if (branch.contains("placeholder-1")) {
                return VersionedSettingsBranch(MASTER_BRANCH)
            }
            return VersionedSettingsBranch(branch)
        }

        private fun determineNightlyPromotionTriggerHour(branchName: String) = when (branchName) {
            MASTER_BRANCH -> 0
            RELEASE_BRANCH -> 1
            else -> {
                val matchResult = OLD_RELEASE_PATTERN.find(branchName)
                if (matchResult == null) {
                    null
                } else {
                    (matchResult.groupValues[1].toInt() - 4).apply {
                        require(this in 2..23)
                    }
                }
            }
        }
    }

    val isMainBranch: Boolean
        get() = isMaster || isRelease
    val isMaster: Boolean
        get() = branchName == MASTER_BRANCH
    val isRelease: Boolean
        get() = branchName == RELEASE_BRANCH
    val isExperimental: Boolean
        get() = branchName == EXPERIMENTAL_BRANCH

    fun vcsRootId() = DslContext.settingsRoot.id.toString()

    fun promoteNightlyTaskName() = nightlyTaskName("promote")
    fun prepNightlyTaskName() = nightlyTaskName("prep")

    fun promoteMilestoneTaskName(): String = when {
        isRelease -> "promoteReleaseMilestone"
        else -> "promoteMilestone"
    }

    fun promoteFinalReleaseTaskName(): String = when {
        isMaster -> throw UnsupportedOperationException("No final release job on master branch")
        isRelease -> "promoteFinalRelease"
        else -> "promoteFinalBackportRelease"
    }

    private fun nightlyTaskName(prefix: String): String = when {
        isMaster -> "${prefix}Nightly"
        isRelease -> "${prefix}ReleaseNightly"
        else -> "${prefix}PatchReleaseNightly"
    }
}
