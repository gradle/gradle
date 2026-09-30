package configurations

import common.BuildToolBuildJvm
import common.DefaultJvm
import common.JvmVendor
import common.JvmVersion
import common.Os
import common.applyDefaultSettings
import common.javaHome
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

            val defaultJavaBinary = "${javaHome(BuildToolBuildJvm, Os.LINUX)}/bin/java"

            applyDefaultSettings(artifactRuleOverride = "")

            params {
                // Disable jdk-provider-plugin, otherwise the JAVA_HOME will be overwritten
                // https://github.com/gradle/teamcity-jdk-provider-plugin/blob/main/teamcity-jdk-provider-plugin-agent/src/main/kotlin/org/gradle/teamcity_jdk_provider_plugin/JdkProviderAgentLifecycleListener.kt#L22
                param("JdkProviderEnabled", "false")
                // should be the same version we run TeamCity with
                param("env.JAVA_HOME", javaHome(DefaultJvm(JvmVersion.JAVA_21, JvmVendor.OPENJDK), Os.LINUX))
            }

            steps {
                script {
                    name = "CHECK_USED_WRAPPER"
                    scriptContent =
                        """
                        set -eu
                        "$defaultJavaBinary" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                        "$defaultJavaBinary" .teamcity/scripts/CheckWrapper.java
                        """.trimIndent()
                }
                if (model.branch.isMaster) {
                    script {
                        name = "CHECK_BAD_MERGE"
                        scriptContent =
                            """
                            set -eu

                            "$defaultJavaBinary" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                            "$defaultJavaBinary" .teamcity/scripts/CheckBadMerge.java
                            """.trimIndent()
                    }
                    script {
                        name = "CHECK_REMOTE_PROJECT_REF"
                        scriptContent =
                            """
                            set -eu
                            "$defaultJavaBinary" .teamcity/scripts/CheckRemoteProjectRef.java ${remoteProjectRefs.joinToString(" ")}
                            """.trimIndent()
                    }
                }
                script {
                    name = "RUN_MAVEN_CLEAN_VERIFY"
                    scriptContent =
                        """
                        set -eu
                        MAVEN_SETTINGS_ARG=""
                        if [ "${'$'}{IGNORE_MIRROR:-false}" != "true" ]; then
                            MAVEN_CENTRAL_MIRROR=${'$'}(printf '%s' "${'$'}{REPO_MIRROR_URLS:-}" | tr ',' '\n' | sed -n 's|^mavencentral:||p')
                            if [ -n "${'$'}MAVEN_CENTRAL_MIRROR" ]; then
                                export MAVEN_CENTRAL_MIRROR_URL="${'$'}{MAVEN_CENTRAL_MIRROR%/}"
                                export MVNW_REPOURL="${'$'}MAVEN_CENTRAL_MIRROR_URL"
                                MAVEN_SETTINGS_ARG="--settings mirror-settings.xml"
                                echo "Using Maven Central mirror: ${'$'}MAVEN_CENTRAL_MIRROR_URL"
                            fi
                        fi
                        ./mvnw ${'$'}MAVEN_SETTINGS_ARG clean verify -Dmaven.repo.local=../build -Dscan.value.gitCommitId=%build.vcs.number% -Dscan.tag.CI -Dscan.value.tcBuildType=${model.projectId}_LightweightChecks
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
