package Gradle_Check_Stage5.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v10.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v10.triggers.vcs

object Gradle_Check_Stage5_Passes : BuildType({
    uuid = "b5e80c13-4f60-4710-a4a1-947f43a1a7c7"
    extId = "Gradle_Check_Stage5_Passes"
    name = "Stage 5 Passes"
    description = "Passes all QA stages"

    artifactRules = "build/build-receipt.properties"

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "createBuildReceipt"
            gradleParams = "-PtimestampedVersion -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
        }
        script {
            name = "RUNNER_165"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                REPO=/home/%env.USER%/.m2/repository
                if [ -e ${'$'}REPO ] ; then
                echo "${'$'}REPO was polluted during the build"
                return -1
                else
                echo "${'$'}REPO does not exist"
                fi
            """.trimIndent()
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                REPO=/home/%env.USER%/.m2/repository
                if [ -e ${'$'}REPO ] ; then
                tree ${'$'}REPO
                rm -rf ${'$'}REPO
                echo "${'$'}REPO was polluted during the build"
                return 1
                else
                echo "${'$'}REPO does not exist"
                fi
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 90
            triggerRules = """
                -:design-docs
                -:subprojects/docs/src/docs/release
            """.trimIndent()
            branchFilter = "+:cc/flaky-fix-it/quick-cross-version-tests"
        }
    }

    dependencies {
        dependency(Gradle_Check_Stage5.buildTypes.Gradle_Check_ColonyCompatibility) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage5.buildTypes.Gradle_Check_SmokeTestsJava8Linux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage4.buildTypes.Gradle_Check_Stage4_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }

        for (bucket in 1..8) {
            dependency("Gradle_Check_TestCoverageCrossVersionJava7Linux_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageCrossVersionJava7Windows_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageForkedJava9Linux_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageParallelJava7IBMLinux_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
