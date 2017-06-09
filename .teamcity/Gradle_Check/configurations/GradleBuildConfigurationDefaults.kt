package configurations

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import model.CIBuildModel
import java.util.Arrays.asList

val gradleParameters: List<String> = asList(
        "-PmaxParallelForks=%maxParallelForks%",
        "-s",
        "--no-daemon",
        "--continue",
        "-I ./gradle/buildScanInit.gradle",
        "-Djava7.home=%linux.jdk.for.gradle.compile%"
)

val gradleBuildCacheParameters: List<String> = asList(
        "--build-cache",
        "-Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password%",
        "-I ./gradle/taskCacheDetailedStatsInit.gradle"
)

val m2CleanScriptLinux = """
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

val m2CleanScriptWindows = """
    IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
        TREE %teamcity.agent.jvm.user.home%\.m2\repository
        RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
        EXIT 1
    )
""".trimIndent()

fun applyDefaultSettings(buildType: BuildType, runsOnWindows: Boolean = false, timeout: Int = 30, vcsRoot: String = "Gradle_Branches_GradlePersonalBranches") {
    buildType.artifactRules = """
        **/build/reports/** => reports
        subprojects/*/build/tmp/test files/** => test-files
        subprojects/*/build/tmp/test distros/**/user-home-dir/daemon/*.log => isolated-daemon
        build/daemon/** => daemon
        build/errorLogs/** => errorLogs
    """.trimIndent()

    buildType.vcs {
        root(vcsRoot)
        checkoutMode = CheckoutMode.ON_AGENT
    }

    buildType.requirements {
        contains("teamcity.agent.jvm.os.name", if (runsOnWindows) "Windows" else "Linux")
    }

    buildType.failureConditions {
        executionTimeoutMin = timeout
    }
}

fun applyDefaults(buildType: BuildType, gradleTasks: String, requiresDistribution: Boolean = false, runsOnWindows: Boolean = false, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}) {
    applyDefaultSettings(buildType, runsOnWindows, timeout)

    buildType.steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean $gradleTasks"
            val parameterString = gradleParameters.joinToString(separator = " ")
            if (runsOnWindows) {
                parameterString.replace("-Djava7.home=%linux.jdk.for.gradle.compile%", "${'"'}-Djava7.home=%linux.jdk.for.gradle.compile%${'"'}")
            }
            gradleParams = parameterString + " " + gradleBuildCacheParameters.joinToString(separator = " ") + " " + extraParameters
            useGradleWrapper = true
        }
    }

    buildType.steps.extraSteps()

    buildType.steps {
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = if (runsOnWindows) m2CleanScriptWindows else m2CleanScriptLinux
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            tasks = "verifyTestFilesCleanup"
            gradleParams = gradleParameters.joinToString(separator = " ")
            useGradleWrapper = true
        }
        gradle {
            name = "TAG_BUILD"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = "gradle/buildTagging.gradle"
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
        }
    }

    applyDefaultDependencies(buildType, requiresDistribution)
}

fun applyDefaultDependencies(buildType: BuildType, requiresDistribution: Boolean = false) {
    if (requiresDistribution) {
        buildType.dependencies {
            dependency("${CIBuildModel.projectPrefix}Stage3_Passes") {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
            artifacts(BuildDistributions) {
                id = "ARTIFACT_DEPENDENCY_${BuildDistributions.extId}"
                cleanDestination = true
                artifactRules = """
                    distributions/*-all.zip => incoming-distributions
                    build-receipt.properties => incoming-distributions
                """.trimIndent()
            }
        }
    } else if (SanityCheck != null) {
        buildType.dependencies {
            dependency(SanityCheck) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
}