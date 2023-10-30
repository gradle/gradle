package configurations

import common.Arch
import common.BuildToolBuildJvm
import common.Jvm
import common.KillProcessMode.KILL_ALL_GRADLE_PROCESSES
import common.KillProcessMode.KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS
import common.KillProcessMode.KILL_PROCESSES_STARTED_BY_GRADLE
import common.Os
import common.VersionedSettingsBranch
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.cleanUpGitUntrackedFilesAndDirectories
import common.cleanUpReadOnlyDir
import common.compileAllDependency
import common.dependsOn
import common.functionalTestParameters
import common.gradleWrapper
import common.killProcessStep
import common.onlyRunOnPreTestedCommitBuildBranch
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep.ExecutionMode
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ProjectFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import model.CIBuildModel
import model.StageName

fun checkCleanDirUnixLike(dir: String, exitOnFailure: Boolean = true) = """
    REPO=$dir
    if [ -e ${'$'}REPO ] ; then
        tree ${'$'}REPO
        rm -rf ${'$'}REPO
        echo "${'$'}REPO was polluted during the build"
        ${if (exitOnFailure) "exit 1" else ""}
    else
        echo "${'$'}REPO does not exist"
    fi

""".trimIndent()

fun checkCleanDirWindows(dir: String, exitOnFailure: Boolean = true) = """
    IF exist $dir (
        TREE $dir
        RMDIR /S /Q $dir
        ${if (exitOnFailure) "EXIT 1" else ""}
    )

""".trimIndent()

fun BuildFeatures.publishBuildStatusToGithub(model: CIBuildModel) {
    if (model.publishStatusToGitHub) {
        publishBuildStatusToGithub()
    }
}

fun BuildFeatures.enablePullRequestFeature() {
    pullRequests {
        vcsRootExtId = VersionedSettingsBranch.fromDslContext().vcsRootId()
        provider = github {
            authType = token {
                token = "%github.bot-teamcity.token%"
            }
            filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            filterTargetBranch = "+:refs/heads/${VersionedSettingsBranch.fromDslContext().branchName}"
        }
    }
}

fun BuildFeatures.publishBuildStatusToGithub() {
    commitStatusPublisher {
        vcsRootExtId = VersionedSettingsBranch.fromDslContext().vcsRootId()
        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = "%github.bot-gradle.token%"
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

fun BaseGradleBuildType.gradleRunnerStep(
    model: CIBuildModel,
    gradleTasks: String,
    os: Os = Os.LINUX,
    extraParameters: String = "",
    daemon: Boolean = true,
    maxParallelForks: String = "%maxParallelForks%",
    isRetry: Boolean = false,
) {
    val stepName: String = if (isRetry) "GRADLE_RETRY_RUNNER" else "GRADLE_RUNNER"
    val stepExecutionMode: ExecutionMode = if (isRetry) ExecutionMode.RUN_ONLY_ON_FAILURE else ExecutionMode.DEFAULT
    val extraBuildScanTags: List<String> = if (isRetry) listOf("RetriedBuild") else emptyList()

    val buildScanTags = model.buildScanTags + listOfNotNull(stage?.id) + extraBuildScanTags
    val parameters = (
        buildToolGradleParameters(daemon, maxParallelForks = maxParallelForks) +
            listOf(extraParameters) +
            buildScanTags.map { buildScanTag(it) } +
            functionalTestParameters(os)
        ).joinToString(separator = " ")

    steps {
        gradleWrapper(this@gradleRunnerStep) {
            name = stepName
            tasks = "clean $gradleTasks"
            gradleParams = parameters
            executionMode = stepExecutionMode
            if (isRetry) {
                onlyRunOnPreTestedCommitBuildBranch()
            }
        }
    }
}

fun applyDefaults(
    model: CIBuildModel,
    buildType: BaseGradleBuildType,
    gradleTasks: String,
    dependsOnQuickFeedbackLinux: Boolean = false,
    os: Os = Os.LINUX,
    extraParameters: String = "",
    timeout: Int = 90,
    daemon: Boolean = true,
    buildJvm: Jvm = BuildToolBuildJvm,
    extraSteps: BuildSteps.() -> Unit = {}
) {
    buildType.applyDefaultSettings(os, timeout = timeout, buildJvm = buildJvm)

    buildType.killProcessStep(KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS, os)
    buildType.steps.cleanUpReadOnlyDir(os)
    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)

    buildType.steps {
        extraSteps()
        checkCleanM2AndAndroidUserHome(os, buildType)
    }

    applyDefaultDependencies(model, buildType, dependsOnQuickFeedbackLinux)
}

private fun BaseGradleBuildType.addRetrySteps(
    model: CIBuildModel,
    gradleTasks: String,
    os: Os = Os.LINUX,
    arch: Arch = Arch.AMD64,
    extraParameters: String = "",
    maxParallelForks: String = "%maxParallelForks%",
    daemon: Boolean = true,
) {
    killProcessStep(KILL_ALL_GRADLE_PROCESSES, os, arch, executionMode = ExecutionMode.RUN_ONLY_ON_FAILURE)
    cleanUpGitUntrackedFilesAndDirectories()
    gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon, maxParallelForks = maxParallelForks, isRetry = true)
}

fun applyTestDefaults(
    model: CIBuildModel,
    buildType: BaseGradleBuildType,
    gradleTasks: String,
    dependsOnQuickFeedbackLinux: Boolean = false,
    buildJvm: Jvm = BuildToolBuildJvm,
    os: Os = Os.LINUX,
    arch: Arch = Arch.AMD64,
    extraParameters: String = "",
    timeout: Int = 90,
    maxParallelForks: String = "%maxParallelForks%",
    extraSteps: BuildSteps.() -> Unit = {}, // the steps after runner steps
    daemon: Boolean = true,
    preSteps: BuildSteps.() -> Unit = {} // the steps before runner steps
) {
    buildType.applyDefaultSettings(os, timeout = timeout, buildJvm = buildJvm, arch = arch)

    buildType.steps {
        preSteps()
    }

    buildType.killProcessStep(KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS, os, arch)
    buildType.steps.cleanUpReadOnlyDir(os)
    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon, maxParallelForks = maxParallelForks)
    buildType.addRetrySteps(model, gradleTasks, os, arch, extraParameters)
    buildType.killProcessStep(KILL_PROCESSES_STARTED_BY_GRADLE, os, arch)

    buildType.steps {
        extraSteps()
        checkCleanM2AndAndroidUserHome(os, buildType)
    }

    applyDefaultDependencies(model, buildType, dependsOnQuickFeedbackLinux)
}

fun buildScanTag(tag: String) = """"-Dscan.tag.$tag""""
fun buildScanCustomValue(key: String, value: String) = """"-Dscan.value.$key=$value""""
fun applyDefaultDependencies(model: CIBuildModel, buildType: BuildType, dependsOnQuickFeedbackLinux: Boolean) {
    if (dependsOnQuickFeedbackLinux) {
        // wait for quick feedback phase to finish successfully
        buildType.dependencies {
            dependsOn(RelativeId(stageTriggerId(model, StageName.QUICK_FEEDBACK_LINUX_ONLY)))
        }
    }
    if (buildType !is CompileAll) {
        buildType.dependencies {
            compileAllDependency(CompileAll.buildTypeId(model))
        }
    }
}
