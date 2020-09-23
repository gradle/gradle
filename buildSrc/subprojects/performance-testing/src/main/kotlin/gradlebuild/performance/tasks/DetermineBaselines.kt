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
import gradlebuild.identity.extension.ModuleIdentityExtension
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import javax.inject.Inject


const val defaultBaseline = "defaults"


const val forceDefaultBaseline = "force-defaults"


const val flakinessDetectionCommitBaseline = "flakiness-detection-commit"


abstract class DetermineBaselines @Inject constructor(@get:Internal val distributed: Boolean) : DefaultTask() {

    @get:Internal
    abstract val configuredBaselines: Property<String>

    @get:Internal
    abstract val determinedBaselines: Property<String>

    @TaskAction
    fun determineForkPointCommitBaseline() {
        if (configuredBaselines.getOrElse("") == forceDefaultBaseline) {
            determinedBaselines.set(defaultBaseline)
        } else if (configuredBaselines.getOrElse("") == flakinessDetectionCommitBaseline) {
            determinedBaselines.set(determineFlakinessDetectionBaseline())
        } else if (!currentBranchIsMasterOrRelease() && !OperatingSystem.current().isWindows && configuredBaselines.isDefaultValue()) {
            // Windows git complains "long path" so we don't build commit distribution on Windows
            determinedBaselines.set(forkPointCommitBaseline())
        } else {
            determinedBaselines.set(configuredBaselines)
        }
    }

    /**
     * Coordinator build doesn't resolve to real commit version, they just pass "flakiness-detection-commit" as it is to worker build
     * "flakiness-detection-commit" is resolved to real commit id in worker build to disable build cache.
     *
     * @see PerformanceTest#NON_CACHEABLE_VERSIONS
     */
    private
    fun determineFlakinessDetectionBaseline() = if (distributed) flakinessDetectionCommitBaseline else currentCommitBaseline()

    private
    fun currentBranchIsMasterOrRelease() = project.the<ModuleIdentityExtension>().gradleBuildBranch.get() in listOf("master", "release")

    private
    fun Property<String>.isDefaultValue() = !isPresent || get() in listOf("", defaultBaseline)

    private
    fun currentCommitBaseline() = commitBaseline(project.execAndGetStdout("git", "rev-parse", "HEAD"))

    private
    fun forkPointCommitBaseline(): String {
        val source = tryGetUpstream() ?: "origin"
        project.execAndGetStdout("git", "fetch", source, "master", "release")
        val masterForkPointCommit = project.execAndGetStdout("git", "merge-base", "origin/master", "HEAD")
        val releaseForkPointCommit = project.execAndGetStdout("git", "merge-base", "origin/release", "HEAD")
        val forkPointCommit =
            if (project.exec { isIgnoreExitValue = true; commandLine("git", "merge-base", "--is-ancestor", masterForkPointCommit, releaseForkPointCommit) }.exitValue == 0)
                releaseForkPointCommit
            else
                masterForkPointCommit
        return commitBaseline(forkPointCommit)
    }

    private
    fun tryGetUpstream(): String? = project.execAndGetStdout("git", "remote", "-v")
        .lines()
        .find { it.contains("git@github.com:gradle/gradle.git") || it.contains("https://github.com/gradle/gradle.git") }
        .let {
            val str = it?.replace(Regex("\\s+"), " ")
            return str?.substring(0, str.indexOf(' '))
        }

    private
    fun commitBaseline(commit: String): String {
        val baseVersionOnForkPoint = project.execAndGetStdout("git", "show", "$commit:version.txt")
        val shortCommitId = project.execAndGetStdout("git", "rev-parse", "--short", commit)
        return "$baseVersionOnForkPoint-commit-$shortCommitId"
    }
}
