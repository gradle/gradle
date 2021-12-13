package common

import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext

data class VersionedSettingsBranch(val branchName: String, val enableTriggers: Boolean) {

    companion object {
        private
        const val MASTER_BRANCH = "master"

        private
        const val RELEASE_BRANCH = "release"

        private
        const val EXPERIMENTAL_BRANCH = "experimental"

        private
        val mainBranches = setOf(MASTER_BRANCH, RELEASE_BRANCH)

        fun fromDslContext(): VersionedSettingsBranch {
            val branch = DslContext.getParameter("Branch")
            // TeamCity uses a dummy name when first running the DSL
            if (branch.contains("placeholder-1")) {
                return VersionedSettingsBranch(MASTER_BRANCH, true)
            }
            return VersionedSettingsBranch(branch.lowercase(), mainBranches.contains(branch.lowercase()))
        }
    }

    val isMaster: Boolean
        get() = branchName == MASTER_BRANCH
    val isRelease: Boolean
        get() = branchName == RELEASE_BRANCH
    val isExperimental: Boolean
        get() = branchName == EXPERIMENTAL_BRANCH
}
