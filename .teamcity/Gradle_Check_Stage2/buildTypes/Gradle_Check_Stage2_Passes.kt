package Gradle_Check_Stage2.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_Stage2_Passes : BuildType({
    uuid = "7a58ec41-01b9-454e-b60a-75ce3850b6be"
    extId = "Gradle_Check_Stage2_Passes"
    name = "Stage 2 Passes"
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
        dependency(Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_Passes) {
            snapshot {
                reuseBuilds = ReuseBuilds.NO
                onDependencyFailure = FailureAction.ADD_PROBLEM
            }
        }

        for (bucket in 8 downTo 1) {
            dependency("Gradle_Check_TestCoverageEmbeddedJava8Linux_$bucket") {
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
