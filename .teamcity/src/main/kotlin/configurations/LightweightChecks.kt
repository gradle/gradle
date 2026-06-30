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

            val os = os
            val defaultJavaBinary = "${javaHome(BuildToolBuildJvm, os)}/bin/java"

            applyDefaultSettings(artifactRuleOverride = "")

            params {
                // Disable jdk-provider-plugin, otherwise the JAVA_HOME will be overwritten
                // https://github.com/gradle/teamcity-jdk-provider-plugin/blob/main/teamcity-jdk-provider-plugin-agent/src/main/kotlin/org/gradle/teamcity_jdk_provider_plugin/JdkProviderAgentLifecycleListener.kt#L22
                param("JdkProviderEnabled", "false")
                // should be the same version we run TeamCity with
                param("env.JAVA_HOME", javaHome(DefaultJvm(JvmVersion.JAVA_21, JvmVendor.OPENJDK), os))
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

                    conditions {
                        doesNotEqual("teamcity.build.branch", BOT_DAILY_UPGRADLE_WRAPPER_BRANCH)
                    }
                }
                script {
                    name = "CHECK_AI_ATTRIBUTION"
                    scriptContent =
                        """
                        set -eu

                        PR_BODY_FILE="${'$'}(mktemp)"
                        trap 'rm -f "${'$'}PR_BODY_FILE"' EXIT

                        # Fetch the body of every open PR that contains the current HEAD SHA.
                        # Silently no-op when the token is unset (master/branch builds without a PR).
                        if [ -n "${'$'}{BOT_TEAMCITY_GITHUB_TOKEN:-}" ]; then
                            curl --silent --show-error --fail-with-body \
                                -H "Accept: application/vnd.github+json" \
                                -H "X-GitHub-Api-Version: 2022-11-28" \
                                -H "Authorization: Bearer ${'$'}BOT_TEAMCITY_GITHUB_TOKEN" \
                                "https://api.github.com/repos/gradle/gradle/commits/%build.vcs.number%/pulls" \
                                | jq -r '.[] | select(.state == "open") | .body // empty' \
                                > "${'$'}PR_BODY_FILE" \
                                || echo "Warning: failed to fetch PR body; skipping PR body scan."
                        else
                            echo "BOT_TEAMCITY_GITHUB_TOKEN not set; skipping PR body scan."
                        fi

                        "$defaultJavaBinary" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                        "$defaultJavaBinary" .teamcity/scripts/CheckAiAttribution.java \
                            --pr-body-file "${'$'}PR_BODY_FILE"
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
                }
                script {
                    name = "CHECK_REMOTE_PROJECT_REF"
                    scriptContent =
                        """
                        set -eu
                        "$defaultJavaBinary" .teamcity/scripts/CheckRemoteProjectRef.java ${remoteProjectRefs.joinToString(" ")}
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
