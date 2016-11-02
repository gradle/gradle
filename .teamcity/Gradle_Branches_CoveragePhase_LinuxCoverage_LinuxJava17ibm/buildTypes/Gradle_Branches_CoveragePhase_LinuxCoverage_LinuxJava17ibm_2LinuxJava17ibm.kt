package Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_2LinuxJava17ibm : BuildType({
    uuid = "0c5e4bd4-f487-411b-9a9d-ded2ec232086"
    extId = "Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_2LinuxJava17ibm"
    name = "(2) Linux - Java 1.7/IBM"
    description = "Full platform coverage for IBM JDK through forked tests [clean platformTest2]"

    artifactRules = """**/build/reports/** => reports
subprojects/*/build/tmp/test files/** => test-files
build/daemon/** => daemon
intTestHomeDir/worker-1/daemon/**/*.log => intTestHome-daemon
build/errorLogs/** => errorLogs"""
    maxRunningBuilds = 3

    params {
        param("env.JAVA_HOME", "%linux.java7.ibm.64bit%")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean platformTest2"
            gradleParams = "-I ./gradle/buildScanInit.gradle -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue"
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
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """REPO=/home/%env.USER%/.m2/repository
if [ -e ${'$'}REPO ] ; then
rm -rf ${'$'}REPO
echo "${'$'}REPO was polluted during the build"
return 1
else
echo "${'$'}REPO does not exist"
fi"""
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
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_1LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_2LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_3LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_4LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_5LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_6LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_7LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.buildTypes.Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18_8LinuxCommitJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
