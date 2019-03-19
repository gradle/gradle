package Gradle_Util.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script

object Gradle_Util_AdHocFunctionalTestLinux : BuildType({
    uuid = "5d59fee9-be42-4f6d-9e0b-fe103e0d2765"
    name = "AdHoc Functional Test - Linux"

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
        param("env.ANDROID_HOME", "/opt/android/sdk")
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
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/init-scripts/build-scan.init.gradle.kts"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            param("ui.gradleRunner.gradle.build.file", "")
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
                    exit 1
                else
                    echo "${'$'}REPO does not exist"
                fi
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
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
