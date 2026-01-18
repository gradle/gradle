package configurations

import common.BuildToolBuildJvm
import common.Os
import common.applyDefaultSettings
import common.javaHome
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

val remoteProjectRefs =
    listOf(
        "androidSmokeTestProjectRef",
        "buildBuilderProjectRef",
        "nowInAndroidBuildProjectRef",
        "largeAndroidBuildProjectRef",
        "largeAndroidBuild2ProjectRef",
        "excludeRuleMergingBuildProjectRef",
        "springBootAppProjectRef",
        "largeNativeBuildProjectRef",
    )

class CheckRemoteProjectRef(
    model: CIBuildModel,
    stage: Stage,
) : OsAwareBaseGradleBuildType(
        os = Os.LINUX,
        stage = stage,
        init = {
            id("${model.projectId}_CheckRemoteProjectRef")
            name = "CheckRemoteProjectRef"
            description = "Check remote project refs in gradle.properties"

            applyDefaultSettings(artifactRuleOverride = "")

            val os = os

            steps {
                script {
                    name = "CheckRemoteProjectRef"
                    scriptContent =
                        "${javaHome(
                            BuildToolBuildJvm,
                            os,
                        )}/bin/java .teamcity/scripts/CheckRemoteProjectRef.java ${remoteProjectRefs.joinToString(" ")}"
                }
            }
        },
    )
