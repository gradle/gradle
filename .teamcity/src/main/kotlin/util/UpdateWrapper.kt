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
import promotion.MILESTONE_BUILD_CONFIGURATION_ID
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
                    "Can be a specific version or one of 'latest', 'release-candidate', 'release-milestone'",
        )
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
        param("env.GITHUB_TOKEN", "%github.bot-gradle.token%")
        param("env.DEFAULT_BRANCH", "%teamcity.build.branch%")
        param("env.TRIGGERED_BY", "%teamcity.build.triggeredBy%")
    }

    fun promotionBuildTypeId(buildConfigurationId: String) = "Gradle_${vcsBranch.branchName.toCapitalized()}_$buildConfigurationId"

    // Only promotions that `PromotionProject` creates for this branch: milestones everywhere, RCs and final releases only on release branches
    val promotions =
        if (vcsBranch.isMaster) {
            mapOf(MILESTONE_BUILD_CONFIGURATION_ID to "milestone")
        } else {
            mapOf(
                FINAL_RELEASE_BUILD_CONFIGURATION_ID to "final-release",
                RELEASE_CANDIDATE_BUILD_CONFIGURATION_ID to "release-candidate",
                MILESTONE_BUILD_CONFIGURATION_ID to "milestone",
            )
        }

    triggers {
        promotions.keys.forEach {
            finishBuildTrigger {
                buildType = promotionBuildTypeId(it)
                successfulOnly = true
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
        promotions.forEach { (buildConfigurationId, suffix) ->
            dependency(AbsoluteId(promotionBuildTypeId(buildConfigurationId))) {
                artifacts {
                    buildRule = lastSuccessful()
                    artifactRules =
                        """
                        ?:version-info.properties => ./version-info-$suffix
                        ?:promote-build-checksums/** => ./checksums-$suffix
                        """.trimIndent()
                }
            }
        }
    }
})
