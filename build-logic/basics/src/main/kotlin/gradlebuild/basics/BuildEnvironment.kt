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
import gradlebuild.toLowerCase
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*


abstract class BuildEnvironmentExtension {
    abstract val gitCommitId: Property<String>
    abstract val gitBranch: Property<String>
    abstract val repoRoot: DirectoryProperty
}


// `generatePrecompiledScriptPluginAccessors` task invokes this method without `gradle.build-environment` applied
private
fun Project.getBuildEnvironmentExtension(): BuildEnvironmentExtension? = rootProject.extensions.findByType(BuildEnvironmentExtension::class.java)


fun Project.repoRoot(): Directory = getBuildEnvironmentExtension()?.repoRoot?.get() ?: layout.projectDirectory.parentOrRoot()


fun Directory.parentOrRoot(): Directory = if (this.file("version.txt").asFile.exists()) {
    this
} else {
    val parent = dir("..")
    when {
        parent.file("version.txt").asFile.exists() -> parent
        this == parent -> throw IllegalStateException("Cannot find 'version.txt' file in root of repository")
        else -> parent.parentOrRoot()
    }
}


fun Project.releasedVersionsFile() = repoRoot().file("released-versions.json")


/**
 * We use command line Git instead of JGit, because JGit's `Repository.resolve` does not work with worktrees.
 */
fun Project.currentGitBranchViaFileSystemQuery(): Provider<String> = getBuildEnvironmentExtension()?.gitBranch ?: objects.property(String::class.java)


fun Project.currentGitCommitViaFileSystemQuery(): Provider<String> = getBuildEnvironmentExtension()?.gitCommitId ?: objects.property(String::class.java)


@Suppress("UnstableApiUsage")
fun Project.git(vararg args: String): String {
    val projectDir = layout.projectDirectory.asFile
    val execOutput = providers.exec {
        workingDir = projectDir
        isIgnoreExitValue = true
        commandLine = listOf("git", *args)
        if (OperatingSystem.current().isWindows) {
            commandLine = listOf("cmd", "/c") + commandLine
        }
    }
    return if (execOutput.result.get().exitValue == 0) execOutput.standardOutput.asText.get().trim()
    else "<unknown>" // It's a source distribution, we don't know.
}


// pre-test/master/queue/alice/feature -> master
// pre-test/release/current/bob/bugfix -> release
fun toPreTestedCommitBaseBranch(actualBranch: String): String = when {
    actualBranch.startsWith("pre-test/") -> actualBranch.substringAfter("/").substringBefore("/")
    else -> actualBranch
}


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
    val architecture = System.getProperty("os.arch").toLowerCase()

    val isCiServer = CI_ENVIRONMENT_VARIABLE in System.getenv()
    val isTravis = "TRAVIS" in System.getenv()
    val isGhActions = "GITHUB_ACTIONS" in System.getenv()
    val isTeamCity = "TEAMCITY_VERSION" in System.getenv()
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
