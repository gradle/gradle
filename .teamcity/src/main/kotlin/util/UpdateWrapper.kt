package util

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.javaHome
import common.requiresOs
import common.toCapitalized
import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import promotion.FINAL_RELEASE_BUILD_CONFIGURATION_ID
import promotion.RELEASE_CANDIDATE_BUILD_CONFIGURATION_ID
import vcsroots.useAbsoluteVcs

object UpdateWrapper : BuildType({
    name = "UpdateWrapper"
    id("UpdateWrapper")

    val vcsBranch = VersionedSettingsBranch.fromDslContext()

    vcs.useAbsoluteVcs(vcsBranch.vcsRootId())

    requirements {
        requiresOs(Os.LINUX)
    }

    params {
        text(
            "wrapperVersion",
            "should-be-overridden",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description =
                "The version of Gradle to update to. " +
                    "Can be a specific version or one of 'latest', 'release-candidate', 'release-milestone', 'release-nightly', 'nightly'.",
        )
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
        param("env.GITHUB_TOKEN", "%github.bot-gradle.token%")
        param("env.DEFAULT_BRANCH", "teamcity.build.branch")
        param("env.TRIGGERED_BY", "%teamcity.build.triggeredBy%")
    }

    if (!vcsBranch.isMaster) {
        listOf(FINAL_RELEASE_BUILD_CONFIGURATION_ID, RELEASE_CANDIDATE_BUILD_CONFIGURATION_ID).forEach {
            triggers {
                finishBuildTrigger {
                    buildType =
                        "Gradle_${vcsBranch.branchName.toCapitalized()}_$it"
                    successfulOnly = true
                }
            }
        }
    }

    steps {
        exec {
            name = "UPDATE_WRAPPER_AND_CREATE_PR"
            path = ".teamcity/scripts/update_wrapper_and_create_pr.sh"
            arguments = "%wrapperVersion%"
        }
    }

    dependencies {
        dependency(AbsoluteId("Gradle_${vcsBranch.branchName.toCapitalized()}_$FINAL_RELEASE_BUILD_CONFIGURATION_ID")) {
            artifacts {
                buildRule = lastSuccessful()
                artifactRules = "version-info.properties => ./version-info-final-release.properties"
            }
        }
        dependency(AbsoluteId("Gradle_${vcsBranch.branchName.toCapitalized()}_$RELEASE_CANDIDATE_BUILD_CONFIGURATION_ID")) {
            artifacts {
                buildRule = lastSuccessful()
                artifactRules = "version-info.properties => ./version-info-release-candidate.properties"
            }
        }
    }
})
