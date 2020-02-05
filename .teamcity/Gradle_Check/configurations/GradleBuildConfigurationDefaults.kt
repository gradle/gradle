package configurations

import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.buildToolParametersString
import common.checkCleanM2
import common.compileAllDependency
import common.gradleWrapper
import common.verifyTestFilesCleanup
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ProjectFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.StageNames

val killAllGradleProcesses = """
    free -m
    ps aux | egrep 'Gradle(Daemon|Worker)'
    ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}' | xargs kill -9
    free -m
    ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}'
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
fun BuildType.attachFileLeakDetector() {
    steps {
        script {
            name = "ATTACH_FILE_LEAK_DETECTOR"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
            "%windows.java11.openjdk.64bit%\bin\java" gradle/AttachAgentToDaemon.java
        """.trimIndent()
        }
    }
}

private
fun BuildType.dumpOpenFiles() {
    steps {
        // This is a workaround for https://youtrack.jetbrains.com/issue/TW-24782
        script {
            name = "SET_BUILD_SUCCESS_ENV"
            executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
            scriptContent = """
                echo "##teamcity[setParameter name='env.PREV_BUILD_STATUS' value='SUCCESS']"
            """.trimIndent()
        }
        script {
            name = "DUMP_OPEN_FILES_ON_FAILURE"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                "%windows.java11.openjdk.64bit%\bin\java" gradle\DumpOpenFilesOnFailure.java
            """.trimIndent()
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
        verifyTestFilesCleanup(daemon, os)
    }

    applyDefaultDependencies(model, buildType, notQuick)
}

fun applyTestDefaults(
    model: CIBuildModel,
    buildType: BaseGradleBuildType,
    gradleTasks: String,
    notQuick: Boolean = false,
    os: Os = Os.linux,
    extraParameters: String = "",
    timeout: Int = 90,
    extraSteps: BuildSteps.() -> Unit = {}, // the steps after runner steps
    daemon: Boolean = true,
    preSteps: BuildSteps.() -> Unit = {} // the steps before runner steps
) {
    if (os == Os.macos) {
        buildType.params.param("env.REPO_MIRROR_URLS", "")
    }

    buildType.applyDefaultSettings(os, timeout)

    buildType.steps {
        preSteps()
    }

    if (os == Os.windows) {
        buildType.attachFileLeakDetector()
    }

    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)

    if (os == Os.windows) {
        buildType.dumpOpenFiles()
    }
    buildType.killProcessStepIfNecessary("KILL_PROCESSES_STARTED_BY_GRADLE", os)

    buildType.steps {
        extraSteps()
        checkCleanM2(os)
        verifyTestFilesCleanup(daemon, os)
    }

    applyDefaultDependencies(model, buildType, notQuick)
}

fun buildScanTag(tag: String) = """"-Dscan.tag.$tag""""
fun buildScanCustomValue(key: String, value: String) = """"-Dscan.value.$key=$value""""

fun applyDefaultDependencies(model: CIBuildModel, buildType: BuildType, notQuick: Boolean = false) {
    if (notQuick) {
        // wait for quick feedback phase to finish successfully
        buildType.dependencies {
            dependency(stageTriggerId(model, StageNames.QUICK_FEEDBACK_LINUX_ONLY)) {
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
