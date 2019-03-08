package configurations

import common.Os
import common.gradleWrapper
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.ProjectFeatures
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import model.CIBuildModel
import model.GradleSubproject
import model.TestCoverage


fun shouldBeSkipped(subProject: GradleSubproject, testConfig: TestCoverage): Boolean {
    // TODO: Hacky. We should really be running all the subprojects on macOS
    // But we're restricting this to just a subset of projects for now
    // since we only have a small pool of macOS agents
    return testConfig.os.ignoredSubprojects.contains(subProject.name)
}

fun gradleParameterString(daemon: Boolean = true) = gradleParameters(daemon).joinToString(separator = " ")

fun gradleParameters(daemon: Boolean = true, isContinue: Boolean = true): List<String> =
        listOf(
                "-PmaxParallelForks=%maxParallelForks%",
                "-s",
                if (daemon) "--daemon" else "--no-daemon",
                if (isContinue) "--continue" else "",
                """-I "%teamcity.build.checkoutDir%/gradle/init-scripts/build-scan.init.gradle.kts"""",
                "-Dorg.gradle.internal.tasks.createops",
                "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%"
        )


val m2CleanScriptUnixLike = """
    REPO=%teamcity.agent.jvm.user.home%/.m2/repository
    if [ -e ${'$'}REPO ] ; then
        tree ${'$'}REPO
        rm -rf ${'$'}REPO
        echo "${'$'}REPO was polluted during the build"
        exit 1
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

fun applyDefaultSettings(buildType: BuildType, os: Os = Os.linux, timeout: Int = 30, vcsRoot: String = "Gradle_Branches_GradlePersonalBranches") {
    buildType.artifactRules = """
        build/report-* => .
        buildSrc/build/report-* => .
        subprojects/*/build/tmp/test files/** => test-files
        build/errorLogs/** => errorLogs
        build/reports/incubation/** => incubation-reports
    """.trimIndent()

    buildType.vcs {
        root(AbsoluteId(vcsRoot))
        checkoutMode = CheckoutMode.ON_AGENT
        buildDefaultBranch = !vcsRoot.contains("Branches")
    }

    buildType.requirements {
        requiresOs(os)
    }

    buildType.failureConditions {
        executionTimeoutMin = timeout
    }

    if (os == Os.linux || os == Os.macos) {
        buildType.params {
            param("env.LC_ALL", "en_US.UTF-8")
        }
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

private
fun BaseGradleBuildType.checkCleanM2Step(os: Os = Os.linux) {
    steps {
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = if (os == Os.windows) m2CleanScriptWindows else m2CleanScriptUnixLike
        }
    }
}

private
fun BaseGradleBuildType.verifyTestFilesCleanupStep(daemon: Boolean = true) {
    steps {
        gradleWrapper {
            name = "VERIFY_TEST_FILES_CLEANUP"
            tasks = "verifyTestFilesCleanup"
            gradleParams = gradleParameterString(daemon)
        }
    }
}

private
fun BaseGradleBuildType.tagBuildStep(model: CIBuildModel, daemon: Boolean = true) {
    steps {
        if (model.tagBuilds) {
            gradleWrapper {
                name = "TAG_BUILD"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "tagBuild"
                gradleParams = "${gradleParameterString(daemon)} -PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
            }
        }
    }
}

private
fun BaseGradleBuildType.gradleRunnerStep(model: CIBuildModel, gradleTasks: String, os: Os = Os.linux, extraParameters: String = "", daemon: Boolean = true) {
    val buildScanTags = model.buildScanTags + listOfNotNull(stage?.id)

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = "clean $gradleTasks"
            gradleParams = (
                    listOf(gradleParameterString(daemon)) +
                            this@gradleRunnerStep.buildCache.gradleParameters(os) +
                            listOf(extraParameters) +
                            "-PteamCityUsername=%teamcity.username.restbot%" +
                            "-PteamCityPassword=%teamcity.password.restbot%" +
                            "-PteamCityBuildId=%teamcity.build.id%" +
                            buildScanTags.map { configurations.buildScanTag(it) }
                    ).joinToString(separator = " ")
        }
    }
}

private
fun BaseGradleBuildType.gradleRerunnerStep(model: CIBuildModel, gradleTasks: String, os: Os = Os.linux, extraParameters: String = "", daemon: Boolean = true) {
    val buildScanTags = model.buildScanTags + listOfNotNull(stage?.id)

    steps {
        gradleWrapper {
            name = "GRADLE_RERUNNER"
            tasks = gradleTasks
            executionMode = BuildStep.ExecutionMode.RUN_ON_FAILURE
            gradleParams = (
                    listOf(gradleParameterString(daemon)) +
                            this@gradleRerunnerStep.buildCache.gradleParameters(os) +
                            listOf(extraParameters) +
                            "-PteamCityUsername=%teamcity.username.restbot%" +
                            "-PteamCityPassword=%teamcity.password.restbot%" +
                            "-PteamCityBuildId=%teamcity.build.id%" +
                            buildScanTags.map { configurations.buildScanTag(it) } +
                            "-PonlyPreviousFailedTestClasses=true"
                    ).joinToString(separator = " ")
        }
    }
}

private
fun BaseGradleBuildType.killProcessStepIfNecessary(stepName: String, os: Os = Os.linux, daemon: Boolean = true) {
    if (os == Os.windows) {
        steps {
            gradleWrapper {
                name = stepName
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "killExistingProcessesStartedByGradle"
                gradleParams = gradleParameterString(daemon)
            }
        }
    }
}

fun applyDefaults(model: CIBuildModel, buildType: BaseGradleBuildType, gradleTasks: String, notQuick: Boolean = false, os: Os = Os.linux, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}, daemon: Boolean = true) {
    applyDefaultSettings(buildType, os, timeout)

    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)

    buildType.steps.extraSteps()

    buildType.checkCleanM2Step(os)
    buildType.verifyTestFilesCleanupStep(daemon)
    buildType.tagBuildStep(model, daemon)

    applyDefaultDependencies(model, buildType, notQuick)
}

fun applyTestDefaults(model: CIBuildModel, buildType: BaseGradleBuildType, gradleTasks: String, notQuick: Boolean = false, os: Os = Os.linux, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}, daemon: Boolean = true) {
    applyDefaultSettings(buildType, os, timeout)

    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)
    buildType.killProcessStepIfNecessary("KILL_PROCESSES_STARTED_BY_GRADLE", os)
    buildType.gradleRerunnerStep(model, gradleTasks, os, extraParameters, daemon)
    buildType.killProcessStepIfNecessary("KILL_PROCESSES_STARTED_BY_GRADLE_RERUN", os)

    buildType.steps.extraSteps()

    buildType.checkCleanM2Step(os)
    buildType.verifyTestFilesCleanupStep(daemon)
    buildType.tagBuildStep(model, daemon)

    applyDefaultDependencies(model, buildType, notQuick)
}

fun buildScanTag(tag: String) = """"-Dscan.tag.$tag""""
fun buildScanCustomValue(key: String, value: String) = """"-Dscan.value.$key=$value""""

fun applyDefaultDependencies(model: CIBuildModel, buildType: BuildType, notQuick: Boolean = false) {
    if (notQuick) {
        // wait for quick feedback phase to finish successfully
        buildType.dependencies {
            dependency(AbsoluteId("${model.projectPrefix}Stage_QuickFeedback_Trigger")) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }

        }
    }

    if (buildType !is CompileAll) {
        buildType.dependencies {
            val compileAllId = CompileAll.buildTypeId(model)
            // Compile All has to succeed before anything else is started
            dependency(AbsoluteId(compileAllId)) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
            // Get the build receipt from sanity check to reuse the timestamp
            artifacts(AbsoluteId(compileAllId)) {
                id = "ARTIFACT_DEPENDENCY_$compileAllId"
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions"
            }
        }
    }
}
