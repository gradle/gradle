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

                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/CheckAiAttribution.java \
                            --pr-body-file "${'$'}PR_BODY_FILE"
                        """.trimIndent()
                }
                script {
                    name = "CHECK_NO_SUBMODULES"
                    scriptContent =
                        """
                        set -eu

                        # Enforce in the merge queue - that is the gate nothing may slip past - and on
                        # ready-for-review PRs. Draft PRs and plain branch builds only get a warning, so
                        # work in progress isn't blocked by a submodule that is still being sorted out.
                        BRANCH="%teamcity.build.branch%"
                        case "${'$'}BRANCH" in
                            gh-readonly-queue/*)
                                WARN_ONLY=""
                                echo "Merge queue branch ${'$'}BRANCH; enforcing."
                                ;;
                            *)
                                # Default to warn-only and upgrade to enforcing only when we can prove
                                # that a non-draft PR contains this commit. An unavailable token or a
                                # failed API call must not turn a draft PR into a hard failure.
                                WARN_ONLY="--warn-only"
                                if [ -n "${'$'}{BOT_TEAMCITY_GITHUB_TOKEN:-}" ]; then
                                    DRAFT_STATES="${'$'}(curl --silent --show-error --fail-with-body \
                                        -H "Accept: application/vnd.github+json" \
                                        -H "X-GitHub-Api-Version: 2022-11-28" \
                                        -H "Authorization: Bearer ${'$'}BOT_TEAMCITY_GITHUB_TOKEN" \
                                        "https://api.github.com/repos/gradle/gradle/commits/%build.vcs.number%/pulls" \
                                        | jq -r '.[] | select(.state == "open") | .draft' \
                                        || echo "")"
                                    if echo "${'$'}DRAFT_STATES" | grep -qx "false"; then
                                        WARN_ONLY=""
                                        echo "Commit belongs to a ready-for-review PR; enforcing."
                                    else
                                        echo "No ready-for-review PR for this commit; warning only."
                                    fi
                                else
                                    echo "BOT_TEAMCITY_GITHUB_TOKEN not set; warning only."
                                fi
                                ;;
                        esac

                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/FindCommits.java ${model.branch.branchName} | \
                        "${'$'}JAVA_HOME/bin/java" .teamcity/scripts/CheckNoSubmodules.java ${'$'}WARN_ONLY
                        """.trimIndent()
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
