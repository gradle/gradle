package Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_2Wi : BuildType({
    uuid = "4ad1d2f4-5c6c-4d3d-8b7d-304ab1dd0bd3"
    extId = "Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_2Wi"
    name = "(2) Windows - Java 1.7 - Cross-version tests"
    description = "Cross-version tests for Windows [clean crossVersionTest2]"

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
            name = "GRADLE_RUNNER"
            tasks = "clean crossVersionTest2"
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
            name = "VERIFY_TEST_FILES_CLEANUP"
            tasks = "verifyTestFilesCleanup"
            gradleParams = "-I ./gradle/buildScanInit.gradle -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue"
            useGradleWrapper = true
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
        executionTimeoutMin = 360
    }

    dependencies {
        dependency(Gradle_Branches_CommitPhase.buildTypes.Gradle_Branches_CommitPhase_BuildDistributions) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }

            artifacts {
                cleanDestination = true
                artifactRules = """distributions/*-all.zip => incoming-distributions
        build-receipt.properties => incoming-distributions"""
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_1WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_2WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_3WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_4WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_5WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_6WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_7WindowsCommitJava) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17.buildTypes.Gradle_Branches_CommitPhase_WindowsCommit_WindowsCommitJava17_8WindowsCommitJava) {
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
