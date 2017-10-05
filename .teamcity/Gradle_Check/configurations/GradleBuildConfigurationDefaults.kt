package configurations

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import model.CIBuildModel
import java.util.Arrays.asList

private val java7HomeLinux = "-Djava7.home=%linux.jdk.for.gradle.compile%"
private val java7Windows = "${'"'}-Djava7.home=%windows.java7.oracle.64bit%${'"'}"

val gradleParameters: List<String> = asList(
        "-PmaxParallelForks=%maxParallelForks%",
        "-s",
        "--daemon",
        "--continue",
        "-I ./gradle/buildScanInit.gradle",
        java7HomeLinux
)

val gradleBuildCacheParameters: List<String> = asList(
        "--build-cache",
        "-Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password%"
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
        build/report-* => .
        buildSrc/build/report-* => .
        subprojects/*/build/tmp/test files/** => test-files
        build/errorLogs/** => errorLogs
    """.trimIndent()

    buildType.vcs {
        root(vcsRoot)
        checkoutMode = CheckoutMode.ON_AGENT
        buildDefaultBranch = !vcsRoot.contains("Branches")
    }

    buildType.requirements {
        contains("teamcity.agent.jvm.os.name", if (runsOnWindows) "Windows" else "Linux")
    }

    buildType.failureConditions {
        executionTimeoutMin = timeout
    }
}

fun BuildFeatures.publishBuildStatusToGithub() {
    commitStatusPublisher {
        vcsRootExtId = "Gradle_Branches_GradlePersonalBranches"
        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = "credentialsJSON:5306bfc7-041e-46e8-8d61-1d49424e7b04"
            }
        }
    }
}

fun ProjectFeatures.buildReportTab(title: String, startPage: String) {
    feature {
        type = "ReportTab"
        param("startPage", startPage)
        param("title", title)
        param("type", "BuildReportTab")
    }
}

fun applyDefaults(model: CIBuildModel, buildType: BuildType, gradleTasks: String, subProject: String = "", notQuick: Boolean = false, runsOnWindows: Boolean = false, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}) {
    applyDefaultSettings(buildType, runsOnWindows, timeout)

    val java7HomeParameter = if (runsOnWindows) java7Windows else java7HomeLinux
    val gradleParameterString = gradleParameters.joinToString(separator = " ").replace(java7HomeLinux, java7HomeParameter)

    buildType.steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean $gradleTasks"
            gradleParams = gradleParameterString + " " + (if (model.buildCacheActive) gradleBuildCacheParameters.joinToString(separator = " ") else "") + " " + extraParameters
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
            gradleParams = gradleParameterString
            useGradleWrapper = true
        }
        if (runsOnWindows) {
            gradle {
                name = "KILL_PROCESSES_STARTED_BY_GRADLE"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "killExistingProcessesStartedByGradle"
                gradleParams = gradleParameterString
                useGradleWrapper = true
            }
        }
        if (model.tagBuilds) {
            gradle {
                name = "TAG_BUILD"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "tagBuild"
                buildFile = "gradle/buildTagging.gradle"
                gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% $java7HomeParameter"
                useGradleWrapper = true
            }
        }
    }

    applyDefaultDependencies(model, buildType, notQuick)
}

fun applyDefaultDependencies(model: CIBuildModel, buildType: BuildType, notQuick: Boolean = false) {
    if (notQuick) {
        // wait for quick feedback phase to finish successfully
        buildType.dependencies {
            dependency("${model.projectPrefix}Stage_QuickFeedback_Trigger") {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }

        }
    }

    if (buildType !is SanityCheck) {
        buildType.dependencies {
            val sanityCheck = SanityCheck(model)
            // Sanity Check has to succeed before anything else is started
            dependency(sanityCheck) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
            // Get the build receipt from sanity check to reuse the timestamp
            artifacts(sanityCheck) {
                id = "ARTIFACT_DEPENDENCY_${sanityCheck.extId}"
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions"
            }
        }
    }
}
