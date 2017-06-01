package Gradle_Check_Stage6.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_SoakTestsJava8Linux : BuildType({
    uuid = "30d2e74a-4276-4d8a-a861-43e9f436cc0f"
    extId = "Gradle_Check_SoakTestsJava8Linux"
    name = "Soak Tests - Java8 Linux"
    description = "Long-running integration tests"

    artifactRules = """
        **/build/reports/** => reports
        subprojects/*/build/tmp/test files/** => test-files
        build/daemon/** => daemon
        intTestHomeDir/worker-1/daemon/**/*.log => intTestHome-daemon
        build/errorLogs/** => errorLogs
    """.trimIndent()
    maxRunningBuilds = 3

    params {
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean soakTest"
            gradleParams = "-PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue --profile"
            useGradleWrapper = true
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            tasks = "verifyTestFilesCleanup"
            gradleParams = "-PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue --profile"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                REPO=/home/%env.USER%/.m2/repository
                if [ -e ${'$'}REPO ] ; then
                rm -rf ${'$'}REPO
                echo "${'$'}REPO was polluted during the build"
                return -1
                else
                echo "${'$'}REPO does not exist"
                fi
            """.trimIndent()
        }
    }

    failureConditions {
        executionTimeoutMin = 60
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
