package Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_3WindowsCommitJava : BuildType({
    uuid = "5b931aaf-92d9-4605-8020-d650241c1dda"
    extId = "Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_3WindowsCommitJava"
    name = "(3) Windows commit - Java 1.7"
    description = "Fast verification on windows through in-process tests [clean quickTest3]"

    artifactRules = """**/build/reports/** => reports
subprojects/*/build/tmp/test files/** => test-files
build/daemon/** => daemon
intTestHomeDir/worker-1/daemon/**/*.log => intTestHome-daemon
build/errorLogs/** => errorLogs"""
    maxRunningBuilds = 3

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "RUNNER_3"
            tasks = "clean quickTest3"
            gradleParams = "-Dorg.gradle.cache.tasks=true  -Dgradle.cache.remote.url=%gradle.cache.remote.url% -I ./gradle/remoteHttpCacheInit.gradle -I ./gradle/taskCacheDetailedStatsInit.gradle -I ./gradle/buildScanInit.gradle -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue"
            useGradleWrapper = true
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            tasks = "verifyTestFilesCleanup"
            gradleParams = "-I ./gradle/buildScanInit.gradle -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            scriptContent = """IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
    RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
    EXIT 1
)"""
        }
        gradle {
            name = "TAG_BUILD"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = "gradle/buildTagging.gradle"
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
            useGradleWrapper = true
        }
    }

    failureConditions {
        executionTimeoutMin = 60
    }

    dependencies {
        dependency(Gradle_Branches_CommitPhase.buildTypes.Gradle_Branches_CommitPhase_SanityCheck) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})
