/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.performance

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*


const val defaultBaseline = "defaults"


const val forceDefaultBaseline = "force-defaults"


const val flakinessDetectionCommitBaseline = "flakiness-detection-commit"


open class DetermineBaselines : DefaultTask() {
    @Internal
    val configuredBaselines = project.objects.property<String>()

    @Internal
    val determinedBaselines = project.objects.property<String>()

    @TaskAction
    fun determineForkPointCommitBaseline() {
        if (configuredBaselines.getOrElse("") == forceDefaultBaseline) {
            determinedBaselines.set(defaultBaseline)
        } else if (configuredBaselines.getOrElse("") == flakinessDetectionCommitBaseline) {
            determinedBaselines.set(currentCommitBaseline())
        } else if (!currentBranchIsMasterOrRelease() && configuredBaselines.isDefaultValue()) {
            determinedBaselines.set(forkPointCommitBaseline())
        } else {
            determinedBaselines.set(configuredBaselines)
        }
    }

    private
    fun currentBranchIsMasterOrRelease() = project.determineCurrentBranch() in listOf("master", "release")

    private
    fun Property<String>.isDefaultValue() = !isPresent || get() in listOf("", defaultBaseline, Config.baseLineList)

    private
    fun currentCommitBaseline() = commitBaseline(project.execAndGetStdout("git", "rev-parse", "HEAD"))

    private
    fun forkPointCommitBaseline(): String {
        project.execAndGetStdout("git", "fetch", "origin", "master", "release")
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
    fun commitBaseline(commit: String): String {
        val baseVersionOnForkPoint = project.execAndGetStdout("git", "show", "$commit:version.txt")
        val shortCommitId = project.execAndGetStdout("git", "rev-parse", "--short", commit)
        return "$baseVersionOnForkPoint-commit-$shortCommitId"
    }
}
