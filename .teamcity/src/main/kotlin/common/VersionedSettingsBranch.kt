package common

import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext

private
val mainBranches = setOf("master", "release")

data class VersionedSettingsBranch(val branchName: String, val enableTriggers: Boolean) {

    companion object {
        val MASTER = VersionedSettingsBranch("master", true)
        val EXPERIMENTAL = VersionedSettingsBranch("experimental", false)

        fun fromDslContext(): VersionedSettingsBranch {
            val branch = DslContext.getParameter("Branch")
            // TeamCity uses a dummy name when first running the DSL
            if (branch.contains("placeholder-1")) {
                return MASTER
            }
            return VersionedSettingsBranch(branch.toLowerCase(), mainBranches.contains(branch.toLowerCase()))
        }
    }
}
