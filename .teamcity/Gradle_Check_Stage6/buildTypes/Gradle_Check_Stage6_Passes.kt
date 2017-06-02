package Gradle_Check_Stage6.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v10.triggers.schedule

object Gradle_Check_Stage6_Passes : BuildType({
    uuid = "ae4e9611-f979-47f4-a46f-d0463635a038"
    extId = "Gradle_Check_Stage6_Passes"
    name = "Stage 6 Passes"

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
            schedulingPolicy = daily {
                hour = 0
                minute = 30
            }
            triggerRules = """
                -:design-docs
                -:subprojects/docs/src/docs/release
            """.trimIndent()
            triggerBuild = always()
            param("revisionRule", "lastFinished")
            param("branchFilter", "+:new-pipeline-master")
            param("dayOfWeek", "Sunday")
        }
    }

    dependencies {
        dependency(Gradle_Check_Stage6.buildTypes.Gradle_Check_PerformanceExperimentsCoordinatorLinux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage6.buildTypes.Gradle_Check_SoakTestsJava8Linux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage6.buildTypes.Gradle_Check_SoakTestsJava8Windows) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage5.buildTypes.Gradle_Check_Stage5_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
