package Gradle_Check_Stage3.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_Stage3_Passes : BuildType({
    uuid = "f71651d5-95cf-4312-adc4-b8185f4985ed"
    extId = "Gradle_Check_Stage3_Passes"
    name = "Stage 3 Passes"
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

    dependencies {
        dependency(Gradle_Check_Stage2.buildTypes.Gradle_Check_Stage2_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.ADD_PROBLEM
            }
        }

        for (bucket in 1..8) {
            dependency("Gradle_Check_TestCoverageEmbeddedJava7Windows_$bucket") {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
