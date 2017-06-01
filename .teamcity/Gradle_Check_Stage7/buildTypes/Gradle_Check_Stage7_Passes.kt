package Gradle_Check_Stage7.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v10.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v10.triggers.schedule

object Gradle_Check_Stage7_Passes : BuildType({
    uuid = "ab5607c6-62c9-47e8-96b7-01036c264cf9"
    extId = "Gradle_Check_Stage7_Passes"
    name = "Stage 7 Passes"

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
        schedule {
            schedulingPolicy = weekly {
                dayOfWeek = ScheduleTrigger.DAY.Saturday
                hour = 1
            }
            triggerRules = """
                -:design-docs
                -:subprojects/docs/src/docs/release
            """.trimIndent()
            triggerBuild = always()
            param("revisionRule", "lastFinished")
            param("branchFilter", "+:master")
        }
    }

    dependencies {
        dependency(Gradle_Check_Stage7.buildTypes.Gradle_Check_PerformanceHistoricalBuild) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage6.buildTypes.Gradle_Check_Stage6_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }

        for (bucket in 1..8) {
            dependency("Gradle_Check_TestCoverageCrossVersionFullJava7Linux_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageCrossVersionFullJava7Windows_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageNoDaemonJava8Linux_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageNoDaemonJava8Windows_$bucket") {
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
