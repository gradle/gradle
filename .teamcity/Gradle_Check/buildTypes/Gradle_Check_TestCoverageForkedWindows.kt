package Gradle_Check.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.Template
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_TestCoverageForkedWindows : Template({
    uuid = "d573a3f9-3b6e-4033-ba54-44359416d135"
    extId = "Gradle_Check_TestCoverageForkedWindows"
    name = "Test Coverage - Forked Windows"

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
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
        param("org.gradle.test.bucket", "")
        param("org.gradle.test.buildType", "")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            id = "RUNNER_98"
            tasks = "clean %org.gradle.test.buildType%Test%org.gradle.test.bucket%"
            gradleParams = """-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle "-Djava7.home=%windows.java7.oracle.64bit%" --build-cache -Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password% -I ./gradle/taskCacheDetailedStatsInit.gradle"""
            useGradleWrapper = true
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
        script {
            name = "CHECK_CLEAN_M2"
            id = "RUNNER_440"
            scriptContent = """
                IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
                    TREE %teamcity.agent.jvm.user.home%\.m2\repository
                    RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
                    EXIT 1
                )
            """.trimIndent()
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            id = "RUNNER_555"
            tasks = "verifyTestFilesCleanup"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle"
            useGradleWrapper = true
        }
        gradle {
            name = "TAG_BUILD"
            id = "RUNNER_1413"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = "gradle/buildTagging.gradle"
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
            useGradleWrapper = true
        }
    }

    failureConditions {
        executionTimeoutMin = 540
    }

    dependencies {
        dependency(Gradle_Check_Stage3.buildTypes.Gradle_Check_Stage3_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        artifacts(Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_BuildDistributions) {
            id = "ARTIFACT_DEPENDENCY_1345"
            cleanDestination = true
            artifactRules = """
                distributions/*-all.zip => incoming-distributions
                build-receipt.properties => incoming-distributions
            """.trimIndent()
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows", "RQ_1899")
    }
})
