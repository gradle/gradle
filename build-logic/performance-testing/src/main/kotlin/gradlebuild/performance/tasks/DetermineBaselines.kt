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

package gradlebuild.performance.tasks

import gradlebuild.basics.kotlindsl.execAndGetStdout
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject


const val FLAKINESS_DETECTION_COMMIT_BASELINE = "flakiness-detection-commit"


interface CommandExecutor {
    fun execAndGetStdout(vararg args: String): String
}


open class DefaultCommandExecutor @Inject constructor(private val exec: ExecOperations) : CommandExecutor {
    override fun execAndGetStdout(vararg args: String): String = exec.execAndGetStdout(*args)
}


@DisableCachingByDefault(because = "Not worth caching")
abstract class DetermineBaselines @Inject constructor(@get:Internal val distributed: Boolean, @get:Internal val commandExecutor: CommandExecutor) : DefaultTask() {

    @get:Internal
    abstract val configuredBaselines: Property<String>

    @get:Internal
    abstract val determinedBaselines: Property<String>

    @get:Internal
    abstract val defaultBaselines: Property<String>

    @get:Internal
    abstract val logicalBranch: Property<String>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    fun determineForkPointCommitBaseline() {
        if (configuredBaselines.getOrElse("") == FLAKINESS_DETECTION_COMMIT_BASELINE) {
            determinedBaselines = determineFlakinessDetectionBaseline()
        } else if (configuredBaselines.getOrElse("").isNotEmpty()) {
            determinedBaselines = configuredBaselines
        } else if (currentBranchIsMasterOrRelease() || isSecurityAdvisoryFork()) {
            determinedBaselines = defaultBaselines
        } else {
            determinedBaselines = forkPointCommitBaseline()
        }

        println("Determined baseline is: ${determinedBaselines.get()}")
    }

    /**
     * Coordinator build doesn't resolve to real commit version, they just pass "flakiness-detection-commit" as it is to worker build
     * "flakiness-detection-commit" is resolved to real commit id in worker build to disable build cache.
     *
     * @see PerformanceTest#NON_CACHEABLE_VERSIONS
     */
    private
    fun determineFlakinessDetectionBaseline() = if (distributed) FLAKINESS_DETECTION_COMMIT_BASELINE else currentCommitBaseline()

    private
    fun currentBranchIsMasterOrRelease() = logicalBranch.get() in listOf("master", "release")

    private
    fun currentCommitBaseline() = commitBaseline(commandExecutor.execAndGetStdout("git", "rev-parse", "HEAD"))

    private
    fun isSecurityAdvisoryFork(): Boolean = commandExecutor.execAndGetStdout("git", "remote", "-v")
        .lines()
        .any { it.contains("gradle/gradle-ghsa") } // ghsa = github-security-advisory

    private
    fun forkPointCommitBaseline(): String {
        val source = tryGetUpstream() ?: "origin"
        commandExecutor.execAndGetStdout("git", "fetch", source, "master", "release")
        val masterForkPointCommit = commandExecutor.execAndGetStdout("git", "merge-base", "origin/master", "HEAD")
        val releaseForkPointCommit = commandExecutor.execAndGetStdout("git", "merge-base", "origin/release", "HEAD")
        val forkPointCommit =
            if (execOperations.exec { isIgnoreExitValue = true; commandLine("git", "merge-base", "--is-ancestor", masterForkPointCommit, releaseForkPointCommit) }.exitValue == 0)
                releaseForkPointCommit
            else
                masterForkPointCommit
        return commitBaseline(forkPointCommit)
    }

    private
    fun tryGetUpstream(): String? = commandExecutor.execAndGetStdout("git", "remote", "-v")
        .lines()
        .find { it.contains("git@github.com:gradle/gradle.git") || it.contains("https://github.com/gradle/gradle.git") }
        .let {
            // origin	https://github.com/gradle/gradle.git (fetch)
            val str = it?.replace(Regex("\\s+"), " ")
            return str?.substring(0, str.indexOf(' '))
        }

    private
    fun commitBaseline(commit: String): String {
        val baseVersionOnForkPoint = commandExecutor.execAndGetStdout("git", "show", "$commit:version.txt")
        val shortCommitId = commandExecutor.execAndGetStdout("git", "rev-parse", "--short", commit)
        return "$baseVersionOnForkPoint-commit-$shortCommitId"
    }
}
