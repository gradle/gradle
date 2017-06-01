package Gradle_Check.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_TestCoverageEmbeddedWindows : Template({
    uuid = "bade6bb2-cd7f-4a69-8542-6e8ce66084f9"
    extId = "Gradle_Check_TestCoverageEmbeddedWindows"
    name = "Test Coverage - Embedded Windows"

    artifactRules = """
        **/build/reports/** => reports
        subprojects/*/build/tmp/test files/** => test-files
        subprojects/*/build/tmp/test distros/**/user-home-dir/daemon/*.log => isolated-daemon
        build/daemon/** => daemon
        intTestHomeDir/worker-1/daemon/**/*.log => intTestHome-daemon
        build/errorLogs/** => errorLogs
    """.trimIndent()
    maxRunningBuilds = 3

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
        param("org.gradle.test.bucket", "")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            id = "RUNNER_629"
            tasks = "clean quickTest%org.gradle.test.bucket%"
            gradleParams = """-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle "-Djava7.home=%windows.java7.oracle.64bit%" --build-cache -Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password% -I ./gradle/taskCacheDetailedStatsInit.gradle"""
            useGradleWrapper = true
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            id = "RUNNER_630"
            tasks = "verifyTestFilesCleanup"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            id = "RUNNER_631"
            scriptContent = """
                IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
                    TREE %teamcity.agent.jvm.user.home%\.m2\repository
                    RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
                    EXIT 1
                )
            """.trimIndent()
        }
        gradle {
            name = "TAG_BUILD"
            id = "RUNNER_330"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = "gradle/buildTagging.gradle"
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
            useGradleWrapper = true
        }
    }

    failureConditions {
        executionTimeoutMin = 60
    }

    dependencies {
        dependency(Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_SanityCheck) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows", "RQ_1822")
    }
})
