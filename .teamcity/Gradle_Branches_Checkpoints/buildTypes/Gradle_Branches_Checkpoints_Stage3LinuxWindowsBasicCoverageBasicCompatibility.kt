package Gradle_Branches_Checkpoints.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Checkpoints_Stage3LinuxWindowsBasicCoverageBasicCompatibility : BuildType({
    uuid = "3e73c1fa-ca94-4199-b6b9-151532cb88b0"
    extId = "Gradle_Branches_Checkpoints_Stage3LinuxWindowsBasicCoverageBasicCompatibility"
    name = "Stage 3 - Linux & Windows Basic Coverage & Basic Compatibility"
    description = "Passes devBuild on linux and windows and compatibility builds"

    artifactRules = "build/build-receipt.properties"

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "createBuildReceipt"
            gradleParams = "-PtimestampedVersion"
            useGradleWrapper = true
        }
        script {
            name = "RUNNER_165"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """REPO=/home/%env.USER%/.m2/repository
if [ -e ${'$'}REPO ] ; then
echo "${'$'}REPO was polluted during the build"
return -1
else
echo "${'$'}REPO does not exist"
fi"""
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
    }

    dependencies {
        dependency(Gradle_Branches_Checkpoints.buildTypes.Gradle_Branches_Checkpoints_Stage2LinuxBasicCoverage) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
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
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
