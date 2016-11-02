package Gradle_Branches_Checkpoints.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Checkpoints_Stage2LinuxBasicCoverage : BuildType({
    uuid = "3de3ae17-ccdc-45eb-b955-22d05280bc52"
    extId = "Gradle_Branches_Checkpoints_Stage2LinuxBasicCoverage"
    name = "Stage 2 - Linux Basic Coverage"
    description = "Passes devBuild on linux"

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
        dependency(Gradle_Branches_Checkpoints.buildTypes.Gradle_Branches_Checkpoints_Stage0Foundation) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
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
        artifacts(Gradle_Branches_CommitPhase.buildTypes.Gradle_Branches_CommitPhase_BuildDistributions) {
            cleanDestination = true
            artifactRules = "distributions/*-bin.zip => incoming-distributions"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
