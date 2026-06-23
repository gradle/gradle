package configurations

import common.Os
import common.applyDefaultSettings
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

private val remoteProjectRefs =
    listOf(
        "androidSmokeTestProjectRef",
        "buildBuilderProjectRef",
        "nowInAndroidBuildProjectRef",
        "largeAndroidBuildProjectRef",
        "largeAndroidBuild2ProjectRef",
        "excludeRuleMergingBuildProjectRef",
        "springBootAppProjectRef",
        "largeNativeBuildProjectRef",
        "isolatedProjectsTestbedRef",
    )

class LightweightChecks(
    model: CIBuildModel,
    stage: Stage,
) : OsAwareBaseGradleBuildType(
        os = Os.LINUX,
        stage = stage,
        init = {
            id("${model.projectId}_LightweightChecks")
            name = "Lightweight Checks"
            description = "Lightweight checks that don't depend on other builds"

            applyDefaultSettings(artifactRuleOverride = "")

            params {
                // Disable jdk-provider-plugin, otherwise the JAVA_HOME will be overwritten
                // https://github.com/gradle/teamcity-jdk-provider-plugin/blob/main/teamcity-jdk-provider-plugin-agent/src/main/kotlin/org/gradle/teamcity_jdk_provider_plugin/JdkProviderAgentLifecycleListener.kt#L22
                param("JdkProviderEnabled", "false")
                param("env.JAVA_HOME", "%teamcity.agent.jvm.java.home%")
            }

            steps {
                script {
                    name = "CHECK_USED_WRAPPER"
                    scriptContent =
                        """
                        set -eu
                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/CheckWrapper.java
                        """.trimIndent()

                    conditions {
                        doesNotEqual("teamcity.build.branch", BOT_DAILY_UPGRADLE_WRAPPER_BRANCH)
                    }
                }
                if (model.branch.isMaster) {
                    script {
                        name = "CHECK_BAD_MERGE"
                        scriptContent =
                            """
                            set -eu

                            "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                            "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/CheckBadMerge.java
                            """.trimIndent()
                    }
                }
                script {
                    name = "CHECK_REMOTE_PROJECT_REF"
                    scriptContent =
                        """
                        set -eu
                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/CheckRemoteProjectRef.java ${remoteProjectRefs.joinToString(" ")}
                        """.trimIndent()
                }
                script {
                    name = "RUN_MAVEN_CLEAN_VERIFY"
                    scriptContent =
                        """
                        ./mvnw clean verify -Dmaven.repo.local=../build -Dscan.value.gitCommitId=%build.vcs.number% -Dscan.tag.CI -Dscan.value.tcBuildType=${model.projectId}_LightweightChecks
                        """.trimIndent()
                    workingDir = ".teamcity"
                }
                script {
                    name = "CLEAN_M2"
                    executionMode = BuildStep.ExecutionMode.ALWAYS
                    scriptContent = checkCleanDirUnixLike("%teamcity.agent.jvm.user.home%/.m2/.develocity", exitOnFailure = false)
                }
            }
        },
    )
