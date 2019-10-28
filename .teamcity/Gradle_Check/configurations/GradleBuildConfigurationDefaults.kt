package configurations

import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.buildToolParametersString
import common.checkCleanM2
import common.compileAllDependency
import common.gradleWrapper
import common.verifyTestFilesCleanup
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.ProjectFeatures
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.commitStatusPublisher
import model.CIBuildModel

val killAllGradleProcessesLinux = """
    free -m
    ps aux | egrep 'Gradle(Daemon|Worker)'
    ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}' | xargs kill -9
    free -m
    ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}'
""".trimIndent()

// TODO: Do some actual killing here
val killAllGradleProcessesWindows = """
    WMIC PROCESS GET processid,commandline
""".trimIndent()

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

fun BuildFeatures.publishBuildStatusToGithub(model: CIBuildModel) {
    if (model.publishStatusToGitHub) {
        publishBuildStatusToGithub()
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
fun BuildSteps.tagBuild(tagBuild: Boolean = true, daemon: Boolean = true) {
    if (tagBuild) {
        gradleWrapper {
            name = "TAG_BUILD"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            gradleParams = "${buildToolParametersString(daemon)} -PteamCityToken=%teamcity.user.bot-gradle.token% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
        }
    }
}

fun BuildSteps.tagBuild(model: CIBuildModel, daemon: Boolean = true) {
    tagBuild(tagBuild = model.tagBuilds, daemon = daemon)
}

private
fun BaseGradleBuildType.gradleRunnerStep(model: CIBuildModel, gradleTasks: String, os: Os = Os.linux, extraParameters: String = "", daemon: Boolean = true) {
    val buildScanTags = model.buildScanTags + listOfNotNull(stage?.id)

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = "clean $gradleTasks"
            gradleParams = (
                buildToolGradleParameters(daemon, os = os) +
                    this@gradleRunnerStep.buildCache.gradleParameters(os) +
                    listOf(extraParameters) +
                    "-PteamCityToken=%teamcity.user.bot-gradle.token%" +
                    "-PteamCityBuildId=%teamcity.build.id%" +
                    buildScanTags.map { buildScanTag(it) }
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
            tasks = "$gradleTasks tagBuild"
            executionMode = BuildStep.ExecutionMode.RUN_ON_FAILURE
            gradleParams = (
                buildToolGradleParameters(daemon, os = os) +
                    this@gradleRerunnerStep.buildCache.gradleParameters(os) +
                    listOf(extraParameters) +
                    "-PteamCityToken=%teamcity.user.bot-gradle.token%" +
                    "-PteamCityBuildId=%teamcity.build.id%" +
                    buildScanTags.map { buildScanTag(it) } +
                    "-PonlyPreviousFailedTestClasses=true" +
                    "-Dscan.tag.RERUN_TESTS" +
                    "-PgithubToken=%github.ci.oauth.token%"
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
                gradleParams = buildToolParametersString(daemon)
            }
        }
    }
}

fun applyDefaults(model: CIBuildModel, buildType: BaseGradleBuildType, gradleTasks: String, notQuick: Boolean = false, os: Os = Os.linux, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}, daemon: Boolean = true) {
    buildType.applyDefaultSettings(os, timeout)

    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)

    buildType.steps {
        extraSteps()
        checkCleanM2(os)
        verifyTestFilesCleanup(daemon)
    }

    applyDefaultDependencies(model, buildType, notQuick)
}

fun applyTestDefaults(model: CIBuildModel, buildType: BaseGradleBuildType, gradleTasks: String, notQuick: Boolean = false, os: Os = Os.linux, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}, daemon: Boolean = true) {
    if (os == Os.macos) {
        buildType.params.param("env.REPO_MIRROR_URLS", "")
    }

    buildType.applyDefaultSettings(os, timeout)

    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)
    buildType.killProcessStepIfNecessary("KILL_PROCESSES_STARTED_BY_GRADLE", os)
    buildType.gradleRerunnerStep(model, gradleTasks, os, extraParameters, daemon)
    buildType.killProcessStepIfNecessary("KILL_PROCESSES_STARTED_BY_GRADLE_RERUN", os)

    buildType.steps {
        extraSteps()
        checkCleanM2(os)
        verifyTestFilesCleanup(daemon)
    }

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
            compileAllDependency(CompileAll.buildTypeId(model))
        }
    }
}
