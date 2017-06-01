package Gradle_Check_Stage4.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_Stage4_Passes : BuildType({
    uuid = "48275d0e-cb33-448b-8567-bc74fe20b8e8"
    extId = "Gradle_Check_Stage4_Passes"
    name = "Stage 4 Passes"
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
            gradleParams = "-PtimestampedVersion -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
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

    dependencies {
        dependency(Gradle_Check_Stage4.buildTypes.Gradle_Check_GradleceptionJava8Linux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage4.buildTypes.Gradle_Check_PerformanceTestCoordinatorLinux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(Gradle_Check_Stage3.buildTypes.Gradle_Check_Stage3_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }

        for (bucket in 1..8) {
            dependency("Gradle_Check_Stage4_TestCoverageForkedJava8Windows_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency("Gradle_Check_TestCoverageForkedJava7Linux_$bucket") {
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
