package Gradle_Branches_SoakTests.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_SoakTests_WindowsSoakTests : BuildType({
    uuid = "a25475f5-9a93-4d36-97aa-4b6b6da4eedc"
    extId = "Gradle_Branches_SoakTests_WindowsSoakTests"
    name = "Windows - Soak tests"
    description = "Long-running integration tests"

    artifactRules = """**/build/reports/** => reports
subprojects/*/build/tmp/test files/** => test-files
build/daemon/** => daemon
intTestHomeDir/worker-1/daemon/**/*.log => intTestHome-daemon
build/errorLogs/** => errorLogs"""
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
            scriptContent = """IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
    RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
    EXIT -1
)"""
        }
    }

    failureConditions {
        executionTimeoutMin = 60
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})
