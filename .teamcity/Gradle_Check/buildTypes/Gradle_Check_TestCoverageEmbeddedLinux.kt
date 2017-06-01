package Gradle_Check.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_TestCoverageEmbeddedLinux : Template({
    uuid = "f363165c-e83a-4d9d-be7b-a74816ff85d4"
    extId = "Gradle_Check_TestCoverageEmbeddedLinux"
    name = "Test Coverage - Embedded Linux"

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
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("gradle.test.bucket", "")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            id = "RUNNER_653"
            tasks = "clean quickTest%gradle.test.bucket%"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle -Djava7.home=%linux.jdk.for.gradle.compile% --build-cache -Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password% -I ./gradle/taskCacheDetailedStatsInit.gradle"
            useGradleWrapper = true
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            id = "RUNNER_654"
            tasks = "verifyTestFilesCleanup"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            id = "RUNNER_655"
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
        gradle {
            name = "TAG_BUILD"
            id = "RUNNER_1452"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = "gradle/buildTagging.gradle"
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% -Djava7.home=%linux.jdk.for.gradle.compile%"
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
        contains("teamcity.agent.jvm.os.name", "Linux", "RQ_1710")
    }
})
