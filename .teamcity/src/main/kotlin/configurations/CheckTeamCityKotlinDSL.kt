package configurations

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

class CheckTeamCityKotlinDSL(
    model: CIBuildModel,
    stage: Stage,
) : OsAwareBaseGradleBuildType(
        os = Os.LINUX,
        stage = stage,
        init = {
            id("${model.projectId}_CheckTeamCityKotlinDSL")
            name = "CheckTeamCityKotlinDSL"
            description = "Check Kotlin DSL in .teamcity/"

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
