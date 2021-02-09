package common

import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext

data class VersionedSettingsBranch(val branchName: String) {

    companion object {
        val MASTER = VersionedSettingsBranch("master")

        fun fromDslContext(): VersionedSettingsBranch {
            val branch = DslContext.getParameter("Branch")
            // TeamCity uses a dummy name when first running the DSL
            if (branch.contains("placeholder-1")) {
                return MASTER
            }
            return VersionedSettingsBranch(branch.toLowerCase())
        }
    }
}
