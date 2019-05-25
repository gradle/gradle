package Gradle_Util.buildTypes

import common.Os
import common.checkCleanM2
import common.gradleWrapper
import common.verifyTestFilesCleanup
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay

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
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = "clean %subproject%:%buildType%"
            gradleParams = """-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/init-scripts/build-scan.init.gradle.kts "-PtestJavaHome=%windows.java8.oracle.64bit%""""
        }
        checkCleanM2(Os.windows)
        verifyTestFilesCleanup()
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
