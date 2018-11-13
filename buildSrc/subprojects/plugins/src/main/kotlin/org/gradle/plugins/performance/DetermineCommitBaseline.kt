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
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*
import javax.inject.Inject


open class DetermineCommitBaseline @Inject constructor(private val commitBaselineVersion: Property<String>) : DefaultTask() {
    @TaskAction
    fun determineForkPointCommitBaseline() {
        if (currentBranchIsMasterOrRelease() && commitBaselineVersion.isDefaultValue()) {
            commitBaselineVersion.set(forkPointCommitBaseline())
        }
    }

    private
    fun currentBranchIsMasterOrRelease() =
        when (project.stringPropertyOrNull(PropertyNames.branchName)) {
            "master" -> true
            "release" -> true
            else -> false
        }

    private
    fun Property<String>.isDefaultValue() = !isPresent || get() in listOf("", "defaults", Config.baseLineList)

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
        val baseVersionOnForkPoint = project.execAndGetStdout("git", "show", "$forkPointCommit:version.txt")
        val shortCommitId = project.execAndGetStdout("git", "rev-parse", "--short", forkPointCommit)
        return "$baseVersionOnForkPoint-commit-$shortCommitId"
    }
}
