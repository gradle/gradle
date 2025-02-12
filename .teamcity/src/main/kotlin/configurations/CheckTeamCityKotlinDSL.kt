package configurations

import common.Os
import common.applyDefaultSettings
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

            applyDefaultSettings(artifactRuleOverride = "")
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
