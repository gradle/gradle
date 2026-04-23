package util

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.javaHome
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import vcsroots.useAbsoluteVcs

object UpdateAgpVersions : BuildType({
    name = "UpdateAgpVersions"
    id("UpdateAgpVersions")

    val vcsBranch = VersionedSettingsBranch.fromDslContext()

    vcs.useAbsoluteVcs(vcsBranch.vcsRootId())

    requirements {
        requiresOs(Os.LINUX)
    }

    params {
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
        param("env.GITHUB_TOKEN", "%github.bot-gradle.token%")
        param("env.DEFAULT_BRANCH", "%teamcity.build.branch%")
    }

    if (vcsBranch.isMaster) {
        triggers {
            schedule {
                schedulingPolicy =
                    weekly {
                        dayOfWeek = ScheduleTrigger.DAY.Saturday
                        hour = 22
                    }
                triggerBuild = always()
            }
        }
    }

    steps {
        exec {
            name = "UPDATE_AGP_AND_CREATE_PR"
            path = ".teamcity/scripts/update_agp_and_create_pr.sh"
        }
    }
})
