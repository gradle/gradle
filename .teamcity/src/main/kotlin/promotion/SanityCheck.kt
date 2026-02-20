package promotion

import common.Os
import common.VersionedSettingsBranch
import common.gradleWrapper
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import vcsroots.useAbsoluteVcs

// Gradle_Master_Promotion_SanityCheck
object SanityCheck : BuildType({
    id("Promotion_SanityCheck")
    name = "SanityCheck"
    description = "Sanity check for promotion project"

    vcs.useAbsoluteVcs(VersionedSettingsBranch.fromDslContext().gradlePromoteVcsRootId())

    steps {
        gradleWrapper {
            tasks = "tasks"
            gradleParams = ""
        }
    }

    triggers {
        vcs {
            branchFilter = ""
            enabled = VersionedSettingsBranch.fromDslContext().isMainBranch
        }
    }

    requirements {
        requiresOs(Os.LINUX)
    }
})
