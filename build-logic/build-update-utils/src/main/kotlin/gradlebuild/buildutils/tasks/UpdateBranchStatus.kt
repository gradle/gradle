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

package gradlebuild.buildutils.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import gradlebuild.basics.toPreTestedCommitBaseBranch
import org.gradle.api.tasks.Internal


/**
 * When a Ready for Nightly trigger build completes successfully on master/release, we publish a git tag
 * green-master/green-release to remote repository so that developers can checkout new branches from these tags.
 */
abstract class UpdateBranchStatus : DefaultTask() {
    @get: Internal
    abstract val gradleBuildBranch: Property<String>
    private
    val logicalBranch: Provider<String>
        get() = gradleBuildBranch.map(::toPreTestedCommitBaseBranch)

    @TaskAction
    fun publishBranchStatus() {
        when (logicalBranch.get()) {
            "master" -> publishBranchStatus("master")
            "release" -> publishBranchStatus("release")
        }
    }

    private
    fun publishBranchStatus(branch: String) {
        val actualBranch = gradleBuildBranch.get()
        println("Publishing branch status of $branch from $actualBranch")
        project.execAndGetStdout("git", "push", "https://bot-teamcity:${System.getenv("BOT_TEAMCITY_GITHUB_TOKEN")}@github.com/gradle/gradle.git", "$actualBranch:green-$branch")
    }

    // FIXME use exectuion service here
    private
    fun Project.execAndGetStdout(vararg args: String) = execAndGetStdout(File("."), *args)

    private
    fun Project.execAndGetStdout(workingDir: File, vararg args: String): String {
        val out = ByteArrayOutputStream()
        exec {
            isIgnoreExitValue = true
            commandLine(*args)
            standardOutput = out
            this.workingDir = workingDir
        }
        return out.toString().trim()
    }
}
