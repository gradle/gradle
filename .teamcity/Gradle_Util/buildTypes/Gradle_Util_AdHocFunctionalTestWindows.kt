package Gradle_Util.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script

object Gradle_Util_AdHocFunctionalTestWindows : BuildType({
    uuid = "944be583-446e-4ecc-b2f2-15f04dd0cfe9"
    name = "AdHoc Functional Test - Windows"

    artifactRules = """
        build/report-* => .
        buildSrc/build/report-* => .
        subprojects/*/build/tmp/test files/** => test-files
        build/errorLogs/** => errorLogs
    """.trimIndent()

    params {
        param("maxParallelForks", "4")
        select("subproject", "", display = ParameterDisplay.PROMPT,
                options = listOf("announce", "antlr", "baseServices", "baseServicesGroovy", "buildCache", "buildCacheHttp", "buildComparison", "buildInit", "buildScanPerformance", "cli", "codeQuality", "compositeBuilds", "core", "coreApi", "dependencyManagement", "diagnostics", "distributions", "docs", "ear", "ide", "ideNative", "idePlay", "installationBeacon", "integTest", "internalAndroidPerformanceTesting", "internalIntegTesting", "internalPerformanceTesting", "internalTesting", "ivy", "jacoco", "javascript", "jvmServices", "languageGroovy", "languageJava", "languageJvm", "languageNative", "languageScala", "launcher", "logging", "maven", "messaging", "modelCore", "modelGroovy", "native", "osgi", "performance", "persistentCache", "platformBase", "platformJvm", "platformNative", "platformPlay", "pluginDevelopment", "pluginUse", "plugins", "processServices", "publish", "reporting", "resources", "resourcesGcs", "resourcesHttp", "resourcesS3", "resourcesSftp", "runtimeApiInfo", "scala", "signing", "smokeTest", "soak", "testKit", "testingBase", "testingJvm", "testingNative", "toolingApi", "toolingApiBuilders", "workers", "wrapper"))
        param("env.JAVA_HOME", "%windows.java9.oracle.64bit%")
        select("buildType", "", display = ParameterDisplay.PROMPT,
                options = listOf("quickTest", "platformTest", "crossVersionTest", "quickFeedbackCrossVersionTest", "parallelTest", "noDaemonTest", "java9SmokeTest"))
    }

    vcs {
        root(AbsoluteId("Gradle_Branches_GradlePersonalBranches"))

        checkoutMode = CheckoutMode.ON_AGENT
        buildDefaultBranch = false
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean %subproject%:%buildType%"
            buildFile = ""
            gradleParams = """-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/init-scripts/build-scan.init.gradle.kts "-PtestJavaHome=%windows.java8.oracle.64bit%""""
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            param("ui.gradleRunner.gradle.build.file", "")
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
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
            tasks = "verifyTestFilesCleanup"
            buildFile = ""
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/init-scripts/build-scan.init.gradle.kts"
            param("ui.gradleRunner.gradle.build.file", "")
        }
        gradle {
            name = "TAG_BUILD"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = ""
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
            param("ui.gradleRunner.gradle.build.file", "")
        }
    }

    failureConditions {
        executionTimeoutMin = 180
    }

    dependencies {
        dependency(AbsoluteId("Gradle_Check_BuildDistributions")) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }

            artifacts {
                cleanDestination = true
                artifactRules = """
                    distributions/*-all.zip => incoming-distributions
                    build-receipt.properties => incoming-distributions
                """.trimIndent()
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})
