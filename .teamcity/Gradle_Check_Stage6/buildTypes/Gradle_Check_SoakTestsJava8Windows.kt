package Gradle_Check_Stage6.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_SoakTestsJava8Windows : BuildType({
    uuid = "6b374151-38b2-47d2-a265-eb6ef95e2723"
    extId = "Gradle_Check_SoakTestsJava8Windows"
    name = "Soak Tests - Java8 Windows"
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
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
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
            scriptContent = """
                IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
                    RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
                    EXIT -1
                )
            """.trimIndent()
        }
    }

    failureConditions {
        executionTimeoutMin = 60
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})
