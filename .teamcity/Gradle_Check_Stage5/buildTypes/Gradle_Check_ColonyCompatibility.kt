package Gradle_Check_Stage5.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_ColonyCompatibility : BuildType({
    uuid = "920f66f3-ad78-4c6d-b8a2-bb65ec5ec8f8"
    extId = "Gradle_Check_ColonyCompatibility"
    name = "Colony Compatibility"
    description = "Check Gradle against latest development version of Colony"

    artifactRules = """
        **/build/reports/** => reports
        **/build/tmp/test files/** => test-files
    """.trimIndent()
    maxRunningBuilds = 3

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root("Colony_ColonyMaster")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean :gradle-test:test"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue"
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

    failureConditions {
        executionTimeoutMin = 120
    }

    dependencies {
        dependency(Gradle_Check_Stage3.buildTypes.Gradle_Check_Stage3_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        artifacts(Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_BuildDistributions) {
            cleanDestination = true
            artifactRules = """
                distributions/*-bin.zip => gradle-test/incoming-distributions/release
                -:distributions/*-test-bin.zip
                build-receipt.properties => incoming-distributions/release
            """.trimIndent()
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
