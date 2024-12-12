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
import common.buildScanTagParam
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.cleanUpGitUntrackedFilesAndDirectories
import common.compileAllDependency
import common.dependsOn
import common.functionalTestParameters
import common.gradleWrapper
import common.killProcessStep
import common.onlyRunOnGitHubMergeQueueBranch
import jetbrains.buildServer.configs.kotlin.BuildFeatures
import jetbrains.buildServer.configs.kotlin.BuildStep.ExecutionMode
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ProjectFeatures
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.parallelTests
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import model.CIBuildModel
import model.StageName

const val GRADLE_RUNNER_STEP_NAME = "GRADLE_RUNNER"
const val GRADLE_RETRY_RUNNER_STEP_NAME = "GRADLE_RETRY_RUNNER"

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

fun BaseGradleBuildType.tcParallelTests(numberOfBatches: Int) {
    if (numberOfBatches > 1) {
        params {
            param("env.TEAMCITY_PARALLEL_TESTS_ENABLED", "1")
        }
        features {
            parallelTests {
                this.numberOfBatches = numberOfBatches
            }
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
    arch: Arch = Arch.AMD64,
    extraParameters: String = "",
    maxParallelForks: String = "%maxParallelForks%",
    isRetry: Boolean = false,
) {
    val stepName: String = if (isRetry) GRADLE_RETRY_RUNNER_STEP_NAME else GRADLE_RUNNER_STEP_NAME
    val stepExecutionMode: ExecutionMode = if (isRetry) ExecutionMode.RUN_ONLY_ON_FAILURE else ExecutionMode.DEFAULT
    val extraBuildScanTags: List<String> = if (isRetry) listOf("RetriedBuild") else emptyList()

    val buildScanTags = model.buildScanTags + listOfNotNull(stage?.id) + extraBuildScanTags
    val parameters = (
        buildToolGradleParameters(maxParallelForks = maxParallelForks) +
            listOf(extraParameters) +
            buildScanTags.map { buildScanTagParam(it) } +
            functionalTestParameters(os, arch)
        ).joinToString(separator = " ") + if (isRetry) " -PretryBuild" else ""

    steps {
        gradleWrapper(this@gradleRunnerStep) {
            name = stepName
            tasks = "clean $gradleTasks"
            gradleParams = parameters
            executionMode = stepExecutionMode
            if (isRetry) {
                onlyRunOnGitHubMergeQueueBranch()
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
    arch: Arch = Arch.AMD64,
    extraParameters: String = "",
    timeout: Int = 90,
    buildJvm: Jvm = BuildToolBuildJvm,
    extraSteps: BuildSteps.() -> Unit = {}
) {
    buildType.applyDefaultSettings(os, timeout = timeout, buildJvm = buildJvm)

    buildType.killProcessStep(KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS, os)
    buildType.gradleRunnerStep(model, gradleTasks, os, arch, extraParameters)

    buildType.steps {
        extraSteps()
        killProcessStep(buildType, KILL_PROCESSES_STARTED_BY_GRADLE, os, arch, executionMode = ExecutionMode.ALWAYS)
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
) {
    killProcessStep(KILL_ALL_GRADLE_PROCESSES, os, arch, executionMode = ExecutionMode.RUN_ONLY_ON_FAILURE)
    cleanUpGitUntrackedFilesAndDirectories()
    gradleRunnerStep(model, gradleTasks, os, arch, extraParameters, maxParallelForks = maxParallelForks, isRetry = true)
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
    preSteps: BuildSteps.() -> Unit = {} // the steps before runner steps
) {
    buildType.applyDefaultSettings(os, timeout = timeout, buildJvm = buildJvm, arch = arch)

    buildType.steps {
        preSteps()
    }

    buildType.killProcessStep(KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS, os, arch)
    buildType.gradleRunnerStep(model, gradleTasks, os, arch, extraParameters, maxParallelForks = maxParallelForks)
    buildType.addRetrySteps(model, gradleTasks, os, arch, extraParameters)
    buildType.killProcessStep(KILL_PROCESSES_STARTED_BY_GRADLE, os, arch, executionMode = ExecutionMode.ALWAYS)

    buildType.steps {
        extraSteps()
        checkCleanM2AndAndroidUserHome(os, buildType)
    }

    applyDefaultDependencies(model, buildType, dependsOnQuickFeedbackLinux)
}

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
