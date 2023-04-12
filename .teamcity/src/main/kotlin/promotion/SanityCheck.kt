package promotion

import common.Os
import common.VersionedSettingsBranch
import common.gradleWrapper
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import vcsroots.gradlePromotionMaster
import vcsroots.useAbsoluteVcs

// Gradle_Master_Promotion_SanityCheck
object SanityCheck : BuildType({
    id("Promotion_SanityCheck")
    name = "SanityCheck"
    description = "Sanity check for promotion project"

    vcs.useAbsoluteVcs(gradlePromotionMaster)

    steps {
        gradleWrapper {
            tasks = "tasks"
            gradleParams = ""
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    triggers {
        vcs {
            branchFilter = ""
            enabled = VersionedSettingsBranch.fromDslContext().enableVcsTriggers
        }
    }

    requirements {
        requiresOs(Os.LINUX)
    }
})
