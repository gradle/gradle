/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.basics

import gradlebuild.basics.BuildParams.CI_ENVIRONMENT_VARIABLE
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*


// `generatePrecompiledScriptPluginAccessors` task invokes this method without `gradle.build-environment` applied
fun Project.getBuildEnvironmentExtension(): BuildEnvironmentExtension = extensions.getByType(BuildEnvironmentExtension::class.java)

fun Project.getBuildEnvironmentExtensionOrNull(): BuildEnvironmentExtension? = extensions.findByType(BuildEnvironmentExtension::class.java)

fun Project.repoRoot(): Directory = getBuildEnvironmentExtensionOrNull()?.repoRoot?.get() ?: layout.projectDirectory.parentOrRoot()

fun Directory.parentOrRoot(): Directory = if (this.file("version.txt").asFile.exists()) {
    this
} else {
    val parent = dir("..")
    when {
        parent.file("version.txt").asFile.exists() -> parent
        this == parent -> error("Cannot find 'version.txt' file in root of repository")
        else -> parent.parentOrRoot()
    }
}


fun Project.releasedVersionsFile() = repoRoot().file("released-versions.json")


/**
 * We use command line Git instead of JGit, because JGit's `Repository.resolve` does not work with worktrees.
 */
fun Project.currentGitBranchViaFileSystemQuery(): Provider<String> = getBuildEnvironmentExtensionOrNull()?.gitBranch ?: objects.property(String::class.java)


fun Project.currentGitCommitViaFileSystemQuery(): Provider<String> = getBuildEnvironmentExtensionOrNull()?.gitCommitId ?: objects.property(String::class.java)


fun Project.scriptTemplateCommitIdViaFileSystemQuery(): Provider<String> = getBuildEnvironmentExtensionOrNull()?.scriptTemplateCommitId ?: objects.property(String::class.java)


/**
 * The build timestamp, computed once per build and identical in every project.
 *
 * Do not compute this per project: on the `install`, `:docs:docsTest` and CI paths the timestamp is
 * the current instant, so every project computing its own would build a distribution whose projects
 * disagree about its version.
 *
 * @see gradlebuild.basics.BuildEnvironmentService.buildTimestamp
 */
fun Project.buildTimestamp(): Provider<String> =
    getBuildEnvironmentExtensionOrNull()?.buildTimestamp ?: localMidnightBuildTimestamp()


/**
 * The timestamp to use where `gradlebuild.build-environment` is not applied, which is the case for the
 * `build-logic`/`build-logic-commons` builds and for the synthetic projects that
 * `generatePrecompiledScriptPluginAccessors` configures. Those still resolve
 * `gradleModule.identity.version`, which is derived from the build timestamp, so a value must be present.
 *
 * None of them produce a distribution, so the exact instant does not matter — but it must be the same in
 * every project of the build. Local midnight is stable by construction, so per-project value sources agree.
 */
private
fun Project.localMidnightBuildTimestamp(): Provider<String> =
    providers.of(BuildTimestampValueSource::class) {
        parameters {
            runningOnCi.set(false)
            runningInstallTask.set(false)
            runningDocsTestTask.set(false)
        }
    }


// gh-readonly-queue/master/pr-1234-5678abcdef -> master
fun toMergeQueueBaseBranch(actualBranch: String): String = when {
    actualBranch.startsWith("gh-readonly-queue/") -> actualBranch.substringAfter("/").substringBefore("/")
    else -> actualBranch
}

/**
 * The build environment.
 *
 * WARNING: Every val in here must not change for they same daemon. If it does, changes will go undetected,
 *          since this whole object is kept in the classloader between builds.
 *          Anything that changes must be in a val with a get() method that recomputes the value each time.
 */
object BuildEnvironment {

    /**
     * A selection of environment variables injected into the environment by the `codeql-env.sh` script.
     */
    private
    val CODEQL_ENVIRONMENT_VARIABLES = arrayOf(
        "CODEQL_JAVA_HOME",
        "CODEQL_EXTRACTOR_JAVA_SCRATCH_DIR",
        "CODEQL_ACTION_RUN_MODE",
        "CODEQL_ACTION_VERSION",
        "CODEQL_DIST",
        "CODEQL_PLATFORM",
        "CODEQL_RUNNER"
    )

    private
    val architecture = System.getProperty("os.arch").lowercase()

    val isCiServer = CI_ENVIRONMENT_VARIABLE in System.getenv()
    val isGhActions = "GITHUB_ACTIONS" in System.getenv()
    val isTeamCity = "TEAMCITY_VERSION" in System.getenv()
    val isTeamCityParallelTestsEnabled
        get() = "TEAMCITY_PARALLEL_TESTS_ENABLED" in System.getenv()
    val isCodeQl: Boolean by lazy {
        // This logic is kept here instead of `codeql-analysis.init.gradle` because that file will hopefully be removed in the future.
        // Removing that file is waiting on the GitHub team fixing an issue in Autobuilder logic.
        CODEQL_ENVIRONMENT_VARIABLES.any { it in System.getenv() }
    }
    val jvm = org.gradle.internal.jvm.Jvm.current()
    val javaVersion = JavaVersion.current()
    val isWindows = OperatingSystem.current().isWindows
    val isLinux = OperatingSystem.current().isLinux
    val isMacOsX = OperatingSystem.current().isMacOsX
    val isIntel: Boolean = architecture == "x86_64" || architecture == "x86"
    val isSlowInternetConnection
        get() = System.getProperty("slow.internet.connection", "false")!!.toBoolean()
    val agentNum: Int
        get() {
            if (System.getenv().containsKey("USERNAME")) {
                val agentNumEnv = System.getenv("USERNAME").replaceFirst("tcagent", "")
                if (Regex("""\d+""").containsMatchIn(agentNumEnv)) {
                    return agentNumEnv.toInt()
                }
            }
            return 1
        }
}
