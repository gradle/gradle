package Gradle_Branches_Checkpoints.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Checkpoints_Stage4FullCoverage : BuildType({
    uuid = "6eec7328-e304-4907-8e22-310339234a0a"
    extId = "Gradle_Branches_Checkpoints_Stage4FullCoverage"
    name = "Stage 4 - Full Coverage"
    description = "Passes devBuild on linux and windows with full test coverage"

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
        dependency(Gradle_Branches_Checkpoints.buildTypes.Gradle_Branches_Checkpoints_Stage3LinuxWindowsBasicCoverageBasicCompatibility) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_1LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_2LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_3LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_4LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_5LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_6LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_7LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_8LinuxJ) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_1L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_2L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_3L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_4L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_5L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_6L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_7L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17DaemonIntegrationTests_8L) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_2) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_3) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_4) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_5) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_6) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_7) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_8) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests_) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_1LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_2LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_3LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_4LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_5LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_6LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_7LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_8LinuxJava17ibm) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_1LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_2LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_3LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_4LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_5LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_6LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_7LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18_8LinuxJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_1LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_2LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_3LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_4LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_5LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_6LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_7LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19.buildTypes.Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava19_8LinuxJava19) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_1Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_2Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_3Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_4Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_5Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_6Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_7Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17CrossVersionTests_8Wi) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_2) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_3) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_4) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_5) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_6) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_7) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTe_8) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest.buildTypes.BuildType_Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava17DaemonIntegrationTest) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_1WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_2WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_3WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_4WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_5WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_6WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_7WindowsJava18) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        dependency(Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18.buildTypes.Gradle_Branches_CoveragePhase_WindowsCoverage_WindowsJava18_8WindowsJava18) {
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
