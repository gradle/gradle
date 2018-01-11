package configurations

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import model.CIBuildModel
import model.GradleSubproject
import model.OS
import model.TestCoverage
import java.util.Arrays.asList

private val java7Homes = mapOf(
        OS.windows to """"-Djava7Home=%windows.java7.oracle.64bit%"""",
        OS.linux to "-Djava7Home=%linux.jdk.for.gradle.compile%",
        // We only have Java 8 on macOS
        OS.macos to "-Djava7Home=%macos.java8.oracle.64bit%"
)

// TODO remove once unused
private val java7HomesOldName = mapOf(
        OS.windows to """"-Djava7.home=%windows.java7.oracle.64bit%"""",
        OS.linux to "-Djava7.home=%linux.jdk.for.gradle.compile%",
        // We only have Java 8 on macOS
        OS.macos to "-Djava7.home=%macos.java8.oracle.64bit%"
)

fun shouldBeSkipped(subProject: GradleSubproject, testConfig: TestCoverage): Boolean {
    // TODO: Hacky. We should really be running all the subprojects on macOS
    // But we're restricting this to just a subset of projects for now
    // since we only have a small pool of macOS agents
    return testConfig.os.ignoredSubprojects.contains(subProject.name)
}

val gradleParameters: List<String> = asList(
        "-PmaxParallelForks=%maxParallelForks%",
        "-s",
        "--daemon",
        "--continue",
        "-I ./gradle/buildScanInit.gradle",
        java7Homes[OS.linux]!!,
        java7HomesOldName[OS.linux]!!
)

val m2CleanScriptUnixLike = """
    REPO=%teamcity.agent.jvm.user.home%/.m2/repository
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

fun applyDefaultSettings(buildType: BuildType, os: OS = OS.linux, timeout: Int = 30, vcsRoot: String = "Gradle_Branches_GradlePersonalBranches") {
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
        contains("teamcity.agent.jvm.os.name", os.agentRequirement)
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

fun applyDefaults(model: CIBuildModel, buildType: BaseGradleBuildType, gradleTasks: String, notQuick: Boolean = false, os: OS = OS.linux, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}) {
    applyDefaultSettings(buildType, os, timeout)

    val java7HomeParameter = java7Homes[os]!!
    val java7HomeParameterOldName = java7HomesOldName[os]!!
    val gradleParameterString = gradleParameters.joinToString(separator = " ")
            .replace(java7Homes[OS.linux]!!, java7HomeParameter)
            .replace(java7HomesOldName[OS.linux]!!, java7HomeParameterOldName)

    buildType.steps {
        gradle {
            name = "CLEAN_BUILD_SRC"
            tasks = "clean"
            gradleParams = gradleParameterString
            useGradleWrapper = true
            workingDir = "buildSrc"
            buildFile = "build.gradle.kts"
            gradleWrapperPath = ".."
        }
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean $gradleTasks"
            gradleParams = (
                    listOf(gradleParameterString) +
                            buildType.buildCache.gradleParameters() +
                            listOf(extraParameters) +
                            model.buildScanTags.map { """"-Dscan.tag.$it"""" }
                    ).joinToString(separator = " ")
            useGradleWrapper = true
        }
    }

    buildType.steps.extraSteps()

    buildType.steps {
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = if (os == OS.windows) m2CleanScriptWindows else m2CleanScriptUnixLike
        }
        gradle {
            name = "VERIFY_TEST_FILES_CLEANUP"
            tasks = "verifyTestFilesCleanup"
            gradleParams = gradleParameterString
            useGradleWrapper = true
        }
        if (os == OS.windows) {
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
                gradleParams = "$gradleParameterString -PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
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
