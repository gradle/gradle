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
            name = "LightweightChecks"
            description = "Lightweight checks that don't depend on other builds"

            val os = os

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
                        "${javaHome(
                            BuildToolBuildJvm,
                            os,
                        )}/bin/java" .teamcity/scripts/CheckWrapper.java ${model.branch.branchName}
                        """.trimIndent()
                }
                if (model.branch.isMaster) {
                    script {
                        name = "CHECK_BAD_MERGE"
                        scriptContent =
                            """
                            set -eu

                            TARGET_REF="refs/remotes/origin/${model.branch.branchName}"
                            if ! git rev-parse --verify "${'$'}TARGET_REF" >/dev/null 2>&1; then
                                echo "Target ref ${'$'}TARGET_REF not present locally; fetching origin/${model.branch.branchName}..."
                                git fetch origin "${model.branch.branchName}"
                            fi

                            TARGET_SHA=$(git rev-parse "${'$'}TARGET_REF")
                            HEAD_SHA=$(git rev-parse HEAD)

                            PARENTS=$(git show --no-patch --format=%P "${'$'}HEAD_SHA")
                            P1=$(echo "${'$'}PARENTS" | awk '{print ${'$'}1}')
                            P2=$(echo "${'$'}PARENTS" | awk '{print ${'$'}2}')

                            PR_HEAD="${'$'}HEAD_SHA"
                            if [ -n "${'$'}P2" ]; then
                                if [ "${'$'}P1" = "${'$'}TARGET_SHA" ]; then
                                    PR_HEAD="${'$'}P2"
                                elif [ "${'$'}P2" = "${'$'}TARGET_SHA" ]; then
                                    PR_HEAD="${'$'}P1"
                                fi
                            fi

                            BASE_SHA=$(git merge-base "${'$'}TARGET_SHA" "${'$'}PR_HEAD")
                            git log --pretty=format:"%H" "${'$'}BASE_SHA..${'$'}PR_HEAD" > pr_commits.txt

                            "${javaHome(
                                BuildToolBuildJvm,
                                os,
                            )}/bin/java" .teamcity/scripts/CheckBadMerge.java pr_commits.txt
                            """.trimIndent()
                    }
                }
                script {
                    name = "CheckRemoteProjectRef"
                    scriptContent =
                        "${javaHome(
                            BuildToolBuildJvm,
                            os,
                        )}/bin/java .teamcity/scripts/CheckRemoteProjectRef.java ${remoteProjectRefs.joinToString(" ")}"
                }
                script {
                    name = "RUN_MAVEN_CLEAN_VERIFY"
                    scriptContent =
                        "./mvnw clean verify -Dmaven.repo.local=../build -Dscan.value.gitCommitId=%build.vcs.number% -Dscan.tag.CI"
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
